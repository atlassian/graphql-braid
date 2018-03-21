package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.language.FieldDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.atlassian.braid.TypeUtils.createDefaultMutationTypeDefinition;
import static com.atlassian.braid.TypeUtils.createDefaultQueryTypeDefinition;
import static com.atlassian.braid.TypeUtils.createSchemaDefinitionIfNecessary;
import static com.atlassian.braid.TypeUtils.findMutationType;
import static com.atlassian.braid.TypeUtils.findQueryType;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;

/**
 * Weaves source schemas into a single executable schema
 */
@SuppressWarnings("WeakerAccess")
public class SchemaBraid<C extends BraidContext> {

    @Deprecated
    public Braid braid(SchemaSource<C>... dataSources) {
        return braid(new TypeDefinitionRegistry(), RuntimeWiring.newRuntimeWiring(), dataSources);
    }

    @Deprecated
    public Braid braid(TypeDefinitionRegistry allTypes, RuntimeWiring.Builder wiringBuilder, SchemaSource<C>... dataSources) {
        SchemaBraidConfiguration.SchemaBraidConfigurationBuilder<C> configBuilder = SchemaBraidConfiguration.<C>builder()
                .typeDefinitionRegistry(allTypes)
                .runtimeWiringBuilder(wiringBuilder);
        Arrays.stream(dataSources).forEach(configBuilder::schemaSource);
        return braid(configBuilder.build());
    }

    public Braid braid(SchemaBraidConfiguration<C> config) {
        final Map<SchemaNamespace, Source<C>> dataSourceTypes = collectDataSources(config);

        final TypeDefinitionRegistry braidTypeRegistry =
                createSchemaDefinitionIfNecessary(config.getTypeDefinitionRegistry());

        final ObjectTypeDefinition queryObjectTypeDefinition =
                findQueryType(braidTypeRegistry)
                        .orElseGet(() -> createDefaultQueryTypeDefinition(braidTypeRegistry));

        final ObjectTypeDefinition mutationObjectTypeDefinition =
                findMutationType(braidTypeRegistry)
                        .orElseGet(() -> createDefaultMutationTypeDefinition(braidTypeRegistry));

        final RuntimeWiring.Builder wiringBuilder = config.getRuntimeWiringBuilder();

        final Map<String, BatchLoader> batchLoaders =
                addDataSources(dataSourceTypes, braidTypeRegistry, wiringBuilder, queryObjectTypeDefinition, mutationObjectTypeDefinition);

        final GraphQLSchema graphQLSchema = new SchemaGenerator()
                .makeExecutableSchema(braidTypeRegistry, wiringBuilder.build());

        return new Braid(graphQLSchema, batchLoaders);
    }

    private Map<String, BatchLoader> addDataSources(Map<SchemaNamespace, Source<C>> dataSources,
                                                    TypeDefinitionRegistry registry,
                                                    RuntimeWiring.Builder runtimeWiringBuilder,
                                                    ObjectTypeDefinition queryObjectTypeDefinition,
                                                    ObjectTypeDefinition mutationObjectTypeDefinition) {
        addAllNonOperationTypes(dataSources, registry);

        final List<FieldDataLoaderRegistration> linkedTypesBatchLoaders = linkTypes(dataSources,
                queryObjectTypeDefinition, mutationObjectTypeDefinition);

        final List<FieldDataLoaderRegistration> queryFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToOperation(dataSources, queryObjectTypeDefinition, Source::getQueryType);

        final List<FieldDataLoaderRegistration> mutationFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToOperation(dataSources, mutationObjectTypeDefinition, Source::getMutationType);

        Map<String, BatchLoader> loaders = new HashMap<>();

        concat(linkedTypesBatchLoaders.stream(),
                concat(queryFieldsBatchLoaders.stream(),
                        mutationFieldsBatchLoaders.stream())).forEach(r -> {
            String key = getDataLoaderKey(r.type, r.field);
            BatchLoader linkBatchLoader = loaders.get(key);
            if (linkBatchLoader != null) {
                loaders.put(key + "-link", linkBatchLoader);
            }

            runtimeWiringBuilder.type(r.type, wiring -> wiring.dataFetcher(r.field, env -> {
                BraidContext ctx = env.getContext();
                DataLoaderRegistry dlRegistry = ctx.getDataLoaderRegistry();
                Object loadedValue = dlRegistry.getDataLoader(key).load(env);
                DataLoader linkLoader = dlRegistry.getDataLoader(key + "-link");
                if (linkLoader != null) {
                    // allows a top level field to also be linked
                    return linkLoader.load(newDataFetchingEnvironment(env)
                            .source(loadedValue)
                            .fieldDefinition(env.getFieldDefinition())
                            .build());
                } else {
                    return loadedValue;
                }
            }));
            loaders.put(key, r.loader);
        });
        return loaders;
    }

    private void addAllNonOperationTypes(Map<SchemaNamespace, Source<C>> dataSources, TypeDefinitionRegistry registry) {
        dataSources.values().forEach(source -> source.getNonOperationTypes().forEach(registry::add));
    }

    private List<FieldDataLoaderRegistration> addSchemaSourcesTopLevelFieldsToOperation(Map<SchemaNamespace, Source<C>> dataSources,
                                                                                        ObjectTypeDefinition braidOperationType,
                                                                                        Function<Source<C>, Optional<ObjectTypeDefinition>> findOperationType) {
        return dataSources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToOperation(source, braidOperationType, findOperationType))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            Source<C> source,
            ObjectTypeDefinition braidMutationType,
            Function<Source<C>, Optional<ObjectTypeDefinition>> findOperationType) {

        return findOperationType.apply(source)
                .map(operationType -> addSchemaSourceTopLevelFieldsToOperation(source.schemaSource, braidMutationType, operationType))
                .orElse(emptyList());
    }

    private List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            SchemaSource<C> schemaSource,
            ObjectTypeDefinition braidOperationType,
            ObjectTypeDefinition sourceOperationType) {

        // todo: smarter merge, optional namespacing, etc
        braidOperationType.getFieldDefinitions().addAll(sourceOperationType.getFieldDefinitions());

        return wireOperationFields(braidOperationType.getName(), schemaSource, sourceOperationType);
    }

    private static <C extends BraidContext> List<FieldDataLoaderRegistration> wireOperationFields(String typeName,
                                                                                                  SchemaSource<C> schemaSource,
                                                                                                  ObjectTypeDefinition sourceOperationType) {
        return sourceOperationType.getFieldDefinitions().stream()
                .map(queryField -> wireOperationField(typeName, schemaSource, queryField))
                .collect(toList());
    }

    private static <C extends BraidContext> FieldDataLoaderRegistration wireOperationField(
            String typeName,
            SchemaSource<C> schemaSource,
            FieldDefinition mutationField) {

        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader =
                newBatchLoader(schemaSource, null);

        return new FieldDataLoaderRegistration(typeName, mutationField.getName(), batchLoader);
    }

    private List<FieldDataLoaderRegistration> linkTypes(Map<SchemaNamespace, Source<C>> sources,
                                                        ObjectTypeDefinition queryObjectTypeDefinition,
                                                        ObjectTypeDefinition mutationObjectTypeDefinition) {
        List<FieldDataLoaderRegistration> fieldDataLoaderRegistrations = new ArrayList<>();
        for (Source<C> source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            HashMap<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());

            for (Link link : source.schemaSource.getLinks()) {
                // replace the field's type
                ObjectTypeDefinition typeDefinition = getObjectTypeDefinition(queryObjectTypeDefinition,
                        mutationObjectTypeDefinition, typeRegistry, dsTypes, link);

                validateSourceFromFieldExists(link, typeDefinition);

                Optional<FieldDefinition> sourceField = typeDefinition.getFieldDefinitions().stream()
                        .filter(d -> d.getName().equals(link.getSourceField()))
                        .findFirst();

                Optional<FieldDefinition> sourceFromField = typeDefinition.getFieldDefinitions().stream()
                        .filter(s -> s != null && s.getName().equals(link.getSourceFromField()))
                        .findAny();
                if (link.isReplaceFromField()) {
                    typeDefinition.getFieldDefinitions().remove(sourceFromField.get());
                }

                Source<C> targetSource = sources.get(link.getTargetNamespace());
                if (targetSource == null) {
                    throw new IllegalArgumentException("Can't find target schema source: " + link.getTargetNamespace());
                }
                if (!targetSource.registry.getType(link.getTargetType()).isPresent()) {
                    throw new IllegalArgumentException("Can't find target type: " + link.getTargetType());

                }

                Type targetType = new TypeName(link.getTargetType());
                if (!sourceField.isPresent()) {
                    // Add source field to schema if not already there
                    if (sourceFromField.isPresent() && isListType(sourceFromField.get().getType())) {
                        targetType = new ListType(targetType);
                    }
                    FieldDefinition field = new FieldDefinition(link.getSourceField(), targetType);
                    typeDefinition.getFieldDefinitions().add(field);
                } else if (isListType(sourceField.get().getType())) {
                    if (sourceField.get().getType() instanceof NonNullType) {
                        sourceField.get().setType(new NonNullType(new ListType(targetType)));
                    } else {
                        sourceField.get().setType(new ListType(targetType));
                    }
                } else {
                    // Change source field type to the braided type
                    sourceField.get().setType(targetType);
                }

                BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader = newBatchLoader(targetSource.schemaSource, link);

                fieldDataLoaderRegistrations.add(new FieldDataLoaderRegistration(link.getSourceType(), link.getSourceField(),
                        batchLoader));
            }
        }
        return fieldDataLoaderRegistrations;
    }

    private boolean isListType(Type type) {
        return type instanceof ListType ||
                (type instanceof NonNullType && ((NonNullType)type).getType() instanceof ListType);
    }

    private ObjectTypeDefinition getObjectTypeDefinition(ObjectTypeDefinition queryObjectTypeDefinition, ObjectTypeDefinition mutationObjectTypeDefinition, TypeDefinitionRegistry typeRegistry, HashMap<String, TypeDefinition> dsTypes, Link link) {
        ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) dsTypes.get(link.getSourceType());
        if (typeDefinition == null && link.getSourceType().equals(queryObjectTypeDefinition.getName())) {
            typeDefinition = findQueryType(typeRegistry).orElse(null);
            if (typeDefinition == null && link.getSourceType().equals(mutationObjectTypeDefinition.getName())) {
                typeDefinition = findMutationType(typeRegistry).orElse(null);
            }
        }

        if (typeDefinition == null) {
            throw new IllegalArgumentException("Can't find source type: " + link.getSourceType());
        }
        return typeDefinition;
    }

    private static String getDataLoaderKey(String sourceType, String sourceField) {
        return sourceType + "." + sourceField;
    }

    private static <C extends BraidContext> BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        // We use DataFetchingEnvironment as the key in the BatchLoader because different fetches of the object may
        // request different fields. Someday we may smartly combine them into one somehow, but that day isn't today.
        return schemaSource.newBatchLoader(schemaSource, link);
    }

    private void validateSourceFromFieldExists(Link link, ObjectTypeDefinition typeDefinition) {
        //noinspection ResultOfMethodCallIgnored
        typeDefinition.getFieldDefinitions().stream()
                .filter(d -> d.getName().equals(link.getSourceFromField()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                format("Can't find source from field: %s", link.getSourceFromField())));
    }

    private static <C extends BraidContext> Map<SchemaNamespace, Source<C>> collectDataSources(SchemaBraidConfiguration<C> config) {
        return config.getSchemaSources()
                .stream()
                .collect(toMap(SchemaSource::getNamespace, Source::new));
    }

    private static final class Source<C extends BraidContext> {
        private final SchemaSource<C> schemaSource;
        private final TypeDefinitionRegistry registry;

        private final ObjectTypeDefinition queryType;
        private final ObjectTypeDefinition mutationType;

        private Source(SchemaSource<C> schemaSource) {
            this.schemaSource = requireNonNull(schemaSource);
            this.registry = schemaSource.getSchema();
            this.queryType = findQueryType(registry).orElse(null);
            this.mutationType = findMutationType(registry).orElse(null);
        }

        Collection<? extends TypeDefinition> getNonOperationTypes() {
            return registry.types().values()
                    .stream()
                    .filter(this::isNotOperationType)
                    .collect(toList());
        }

        public Optional<ObjectTypeDefinition> getQueryType() {
            return Optional.ofNullable(queryType);
        }

        public Optional<ObjectTypeDefinition> getMutationType() {
            return Optional.ofNullable(mutationType);
        }

        boolean isNotOperationType(TypeDefinition typeDefinition) {
            return !isOperationType(typeDefinition);
        }

        boolean isOperationType(TypeDefinition typeDefinition) {
            requireNonNull(typeDefinition);
            return Objects.equals(queryType, typeDefinition) || Objects.equals(mutationType, typeDefinition);
        }
    }

    private static class FieldDataLoaderRegistration {
        private final String type;
        private final String field;
        private final BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader;

        private FieldDataLoaderRegistration(String type, String field, BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader) {
            this.type = type;
            this.field = field;
            this.loader = loader;
        }
    }
}
