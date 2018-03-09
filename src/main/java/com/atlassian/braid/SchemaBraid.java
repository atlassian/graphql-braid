package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
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
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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

        final List<BatchLoader> batchLoaders =
                addDataSources(dataSourceTypes, braidTypeRegistry, wiringBuilder, queryObjectTypeDefinition, mutationObjectTypeDefinition);

        final GraphQLSchema graphQLSchema = new SchemaGenerator()
                .makeExecutableSchema(braidTypeRegistry, wiringBuilder.build());

        return new Braid(graphQLSchema, batchLoaders);
    }

    private List<BatchLoader> addDataSources(Map<SchemaNamespace, Source<C>> dataSources,
                                             TypeDefinitionRegistry registry,
                                             RuntimeWiring.Builder runtimeWiringBuilder,
                                             ObjectTypeDefinition queryObjectTypeDefinition,
                                             ObjectTypeDefinition mutationObjectTypeDefinition) {
        addAllNonOperationTypes(dataSources, registry);

        final List<BatchLoader> queryFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToOperation(dataSources, runtimeWiringBuilder, queryObjectTypeDefinition, Source::getQueryType);

        final List<BatchLoader> mutationFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToOperation(dataSources, runtimeWiringBuilder, mutationObjectTypeDefinition, Source::getMutationType);

        final List<BatchLoader> linkedTypesBatchLoaders = linkTypes(dataSources, runtimeWiringBuilder);

        List<BatchLoader> batchLoaders = new LinkedList<>();
        batchLoaders.addAll(queryFieldsBatchLoaders);
        batchLoaders.addAll(mutationFieldsBatchLoaders);
        batchLoaders.addAll(linkedTypesBatchLoaders);
        return batchLoaders;
    }

    private void addAllNonOperationTypes(Map<SchemaNamespace, Source<C>> dataSources, TypeDefinitionRegistry registry) {
        dataSources.values().forEach(source -> source.getNonOperationTypes().forEach(registry::add));
    }

    private List<BatchLoader> addSchemaSourcesTopLevelFieldsToOperation(Map<SchemaNamespace, Source<C>> dataSources,
                                                                        RuntimeWiring.Builder runtimeWiringBuilder,
                                                                        ObjectTypeDefinition braidOperationType,
                                                                        Function<Source<C>, Optional<ObjectTypeDefinition>> findOperationType) {
        return dataSources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToOperation(source, runtimeWiringBuilder, braidOperationType, findOperationType))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToOperation(
            Source<C> source, RuntimeWiring.Builder wiringBuilder, ObjectTypeDefinition braidMutationType, Function<Source<C>, Optional<ObjectTypeDefinition>> findOperationType) {

        return findOperationType.apply(source)
                .map(operationType -> addSchemaSourceTopLevelFieldsToOperation(source.schemaSource, wiringBuilder, braidMutationType, operationType))
                .orElse(emptyList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToOperation(
            SchemaSource<C> schemaSource,
            RuntimeWiring.Builder runtimeWiringBuilder,
            ObjectTypeDefinition braidOperationType,
            ObjectTypeDefinition sourceOperationType) {

        final List<BatchLoader> result = new ArrayList<>();

        // todo: smarter merge, optional namespacing, etc
        braidOperationType.getFieldDefinitions().addAll(sourceOperationType.getFieldDefinitions());

        runtimeWiringBuilder.type(braidOperationType.getName(),
                typeRuntimeWiringBuilder -> {
                    result.addAll(wireOperationFields(typeRuntimeWiringBuilder, schemaSource, sourceOperationType));
                    return typeRuntimeWiringBuilder;
                });

        return result;
    }

    private static <C extends BraidContext> List<BatchLoader> wireOperationFields(TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                                                                  SchemaSource<C> schemaSource,
                                                                                  ObjectTypeDefinition sourceOperationType) {
        return sourceOperationType.getFieldDefinitions().stream()
                .map(queryField -> wireOperationField(typeRuntimeWiringBuilder, schemaSource, queryField))
                .collect(toList());
    }

    private static <C extends BraidContext> BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> wireOperationField(
            TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
            SchemaSource<C> schemaSource,
            FieldDefinition mutationField) {

        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> batchLoader =
                newBatchLoader(schemaSource, null);

        typeRuntimeWiringBuilder.dataFetcher(mutationField.getName(), environment -> {
            DataLoaderRegistry registry = environment.<BraidContext>getContext().getDataLoaderRegistry();
            return registry.getDataLoader(batchLoader.toString()).load(environment);
        });

        return batchLoader;
    }

    private List<BatchLoader> linkTypes(Map<SchemaNamespace, Source<C>> sources, RuntimeWiring.Builder wiringBuilder) {
        List<BatchLoader> batchLoaders = new ArrayList<>();
        for (Source<C> source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            HashMap<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());

            for (Link link : source.schemaSource.getLinks()) {
                // replace the field's type
                ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) dsTypes.get(link.getSourceType());

                validateSourceFromFieldExists(link, typeDefinition);

                Optional<FieldDefinition> sourceField = typeDefinition.getFieldDefinitions().stream()
                        .filter(d -> d.getName().equals(link.getSourceField()))
                        .findFirst();

                if (link.isReplaceFromField()) {
                    typeDefinition.getFieldDefinitions().stream()
                            .filter(s -> s instanceof FieldDefinition
                                    && s.getName().equals(link.getSourceFromField()))
                            .findAny()
                            .ifPresent(s -> typeDefinition.getFieldDefinitions().remove(s));
                }

                // todo: support different target types like list
                Source<C> targetSource = sources.get(link.getTargetNamespace());
                if (!targetSource.registry.getType(link.getTargetType()).isPresent()) {
                    throw new IllegalArgumentException("Can't find target type: " + link.getTargetType());

                }

                TypeName targetType = new TypeName(link.getTargetType());
                if (!sourceField.isPresent()) {
                    // Add source field to schema if not already there
                    FieldDefinition field = new FieldDefinition(link.getSourceField(), targetType);
                    typeDefinition.getFieldDefinitions().add(field);
                } else {
                    // Change source field type to the braided type
                    sourceField.get().setType(targetType);
                }

                BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> batchLoader = newBatchLoader(targetSource.schemaSource, link);
                batchLoaders.add(batchLoader);

                // wire in the field resolver to the target data source
                wiringBuilder.type(link.getSourceType(), typeWiring -> typeWiring.dataFetcher(link.getSourceField(), environment -> {
                            DataLoaderRegistry registry = environment.<BraidContext>getContext().getDataLoaderRegistry();
                            return registry.getDataLoader(batchLoader.toString()).load(environment);
                        }
                ));
            }
        }
        return batchLoaders;
    }

    private static <C extends BraidContext> BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
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
}
