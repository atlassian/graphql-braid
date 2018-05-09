package com.atlassian.braid;

import com.atlassian.braid.graphql.language.AliasablePropertyDataFetcher;
import graphql.execution.DataFetcherResult;
import graphql.language.FieldDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import static com.atlassian.braid.java.util.BraidCollectors.singleton;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

final class BraidSchema {

    private final GraphQLSchema schema;
    private final Map<String, BatchLoader> batchLoaders;

    private BraidSchema(GraphQLSchema schema, Map<String, BatchLoader> batchLoaders) {
        this.schema = requireNonNull(schema);
        this.batchLoaders = requireNonNull(batchLoaders);
    }


    static BraidSchema from(TypeDefinitionRegistry typeDefinitionRegistry,
                            RuntimeWiring.Builder runtimeWiringBuilder,
                            List<SchemaSource> schemaSources) {

        final Map<SchemaNamespace, BraidSchemaSource> dataSourceTypes = toBraidSchemaSourceMap(schemaSources);

        final TypeDefinitionRegistry braidTypeRegistry =
                createSchemaDefinitionIfNecessary(typeDefinitionRegistry);

        final ObjectTypeDefinition queryObjectTypeDefinition =
                findQueryType(braidTypeRegistry)
                        .orElseGet(() -> createDefaultQueryTypeDefinition(braidTypeRegistry));

        final ObjectTypeDefinition mutationObjectTypeDefinition =
                findMutationType(braidTypeRegistry)
                        .orElseGet(() -> createDefaultMutationTypeDefinition(braidTypeRegistry));

        final Map<String, BatchLoader> batchLoaders =
                addDataSources(dataSourceTypes, braidTypeRegistry, runtimeWiringBuilder, queryObjectTypeDefinition, mutationObjectTypeDefinition);

        final GraphQLSchema graphQLSchema = new SchemaGenerator()
                .makeExecutableSchema(braidTypeRegistry, runtimeWiringBuilder.build());

        return new BraidSchema(graphQLSchema, batchLoaders);
    }

    private static Map<String, BatchLoader> addDataSources(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                                                           TypeDefinitionRegistry registry,
                                                           RuntimeWiring.Builder runtimeWiringBuilder,
                                                           ObjectTypeDefinition queryObjectTypeDefinition,
                                                           ObjectTypeDefinition mutationObjectTypeDefinition) {
        addAllNonOperationTypes(dataSources, registry, runtimeWiringBuilder);

        final List<FieldDataLoaderRegistration> linkedTypesBatchLoaders = linkTypes(dataSources,
                queryObjectTypeDefinition, mutationObjectTypeDefinition);

        final List<FieldDataLoaderRegistration> queryFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToOperation(dataSources, queryObjectTypeDefinition, BraidSchemaSource::getQueryType);

        final List<FieldDataLoaderRegistration> mutationFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToOperation(dataSources, mutationObjectTypeDefinition, BraidSchemaSource::getMutationType);

        Map<String, BatchLoader> loaders = new HashMap<>();

        concat(linkedTypesBatchLoaders.stream(),
                concat(queryFieldsBatchLoaders.stream(),
                        mutationFieldsBatchLoaders.stream())).forEach(r -> {
            String key = getDataLoaderKey(r.type, r.field);
            BatchLoader linkBatchLoader = loaders.get(key);
            if (linkBatchLoader != null) {
                loaders.put(key + "-link", linkBatchLoader);
            }

            runtimeWiringBuilder.type(r.type, wiring -> wiring.dataFetcher(r.field, new BraidDataFetcher(key)));
            loaders.put(key, r.loader);
        });
        return loaders;
    }

    Map<String, BatchLoader> getBatchLoaders() {
        return Collections.unmodifiableMap(batchLoaders);
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    private static class BraidDataFetcher implements DataFetcher {
        private final String dataLoaderKey;

        private BraidDataFetcher(String dataLoaderKey) {
            this.dataLoaderKey = requireNonNull(dataLoaderKey);
        }

        @Override
        public Object get(DataFetchingEnvironment env) {
            final DataLoaderRegistry registry = getDataLoaderRegistry(env);
            final Object loadedValue = registry.getDataLoader(dataLoaderKey).load(env);

            // allows a top level field to also be linked
            return Optional.ofNullable(registry.getDataLoader(dataLoaderKey + "-link"))
                    .map(l -> loadFromLinkLoader(env, loadedValue, l))
                    .orElse(loadedValue);
        }

        private static Object loadFromLinkLoader(DataFetchingEnvironment env,
                                                 Object source,
                                                 DataLoader<Object, Object> dataLoader) {
            return dataLoader.load(newDataFetchingEnvironment(env)
                    .source(source)
                    .fieldDefinition(env.getFieldDefinition())
                    .build());
        }

        private static DataLoaderRegistry getDataLoaderRegistry(DataFetchingEnvironment env) {
            return getContext(env).getDataLoaderRegistry();
        }

        private static BraidContext getContext(DataFetchingEnvironment env) {
            return env.getContext();
        }
    }


    private static void addAllNonOperationTypes(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                                                TypeDefinitionRegistry registry,
                                                RuntimeWiring.Builder runtimeWiringBuilder) {
        dataSources.values().forEach(source -> {
            source.getNonOperationTypes().forEach(type -> {
                registry.add(type);
                if (type instanceof ObjectTypeDefinition) {
                    ((ObjectTypeDefinition) type).getFieldDefinitions().forEach(fd -> {
                        runtimeWiringBuilder.type(type.getName(), wiring -> wiring.dataFetcher(fd.getName(),
                                new AliasablePropertyDataFetcher(fd.getName())));
                    });
                }
            });
        });
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourcesTopLevelFieldsToOperation(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                                                                                               ObjectTypeDefinition braidOperationType,
                                                                                               Function<BraidSchemaSource, Optional<ObjectTypeDefinition>> findOperationType) {
        return dataSources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToOperation(source, braidOperationType, findOperationType))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            BraidSchemaSource source,
            ObjectTypeDefinition braidMutationType,
            Function<BraidSchemaSource, Optional<ObjectTypeDefinition>> findOperationType) {

        return findOperationType.apply(source)
                .map(operationType -> addSchemaSourceTopLevelFieldsToOperation(source.schemaSource, braidMutationType, operationType))
                .orElse(emptyList());
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            SchemaSource schemaSource,
            ObjectTypeDefinition braidOperationType,
            ObjectTypeDefinition sourceOperationType) {

        // todo: smarter merge, optional namespacing, etc
        braidOperationType.getFieldDefinitions().addAll(sourceOperationType.getFieldDefinitions());

        return wireOperationFields(braidOperationType.getName(), schemaSource, sourceOperationType);
    }

    private static List<FieldDataLoaderRegistration> wireOperationFields(String typeName,
                                                                         SchemaSource schemaSource,
                                                                         ObjectTypeDefinition sourceOperationType) {
        return sourceOperationType.getFieldDefinitions().stream()
                .map(queryField -> wireOperationField(typeName, schemaSource, queryField))
                .collect(toList());
    }

    private static FieldDataLoaderRegistration wireOperationField(
            String typeName,
            SchemaSource schemaSource,
            FieldDefinition mutationField) {

        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader =
                newBatchLoader(schemaSource, null);

        return new FieldDataLoaderRegistration(typeName, mutationField.getName(), batchLoader);
    }

    private static List<FieldDataLoaderRegistration> linkTypes(Map<SchemaNamespace, BraidSchemaSource> sources,
                                                               ObjectTypeDefinition queryObjectTypeDefinition,
                                                               ObjectTypeDefinition mutationObjectTypeDefinition) {
        List<FieldDataLoaderRegistration> fieldDataLoaderRegistrations = new ArrayList<>();
        for (BraidSchemaSource source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            Map<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());

            for (Link link : source.schemaSource.getLinks()) {
                // replace the field's type
                ObjectTypeDefinition typeDefinition = getObjectTypeDefinition(queryObjectTypeDefinition,
                        mutationObjectTypeDefinition, typeRegistry, dsTypes, link);

                validateSourceFromFieldExists(link, typeDefinition);

                Optional<FieldDefinition> sourceField = typeDefinition.getFieldDefinitions().stream()
                        .filter(d -> d.getName().equals(link.getSourceField()))
                        .findFirst();

                Optional<FieldDefinition> sourceFromField = typeDefinition.getFieldDefinitions()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(s -> s.getName().equals(link.getSourceFromField()))
                        .findAny();

                if (link.isReplaceFromField()) {
                    typeDefinition.getFieldDefinitions().remove(sourceFromField.get());
                }

                BraidSchemaSource targetSource = sources.get(link.getTargetNamespace());
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

                fieldDataLoaderRegistrations.add(new FieldDataLoaderRegistration(
                        link.getSourceType(),
                        link.getSourceField(),
                        newBatchLoader(targetSource.schemaSource, link)));
            }
        }
        return fieldDataLoaderRegistrations;
    }

    private static boolean isListType(Type type) {
        return type instanceof ListType ||
                (type instanceof NonNullType && ((NonNullType) type).getType() instanceof ListType);
    }

    private static ObjectTypeDefinition getObjectTypeDefinition(ObjectTypeDefinition queryObjectTypeDefinition,
                                                                ObjectTypeDefinition mutationObjectTypeDefinition,
                                                                TypeDefinitionRegistry typeRegistry,
                                                                Map<String, TypeDefinition> dsTypes,
                                                                Link link) {
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

    private static BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource, Link link) {
        // We use DataFetchingEnvironment as the key in the BatchLoader because different fetches of the object may
        // request different fields. Someday we may smartly combine them into one somehow, but that day isn't today.
        return schemaSource.newBatchLoader(schemaSource, link);
    }

    private static void validateSourceFromFieldExists(Link link, ObjectTypeDefinition typeDefinition) {
        //noinspection ResultOfMethodCallIgnored
        typeDefinition.getFieldDefinitions().stream()
                .filter(d -> d.getName().equals(link.getSourceFromField()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                format("Can't find source from field: %s", link.getSourceFromField())));
    }

    private static Map<SchemaNamespace, BraidSchemaSource> toBraidSchemaSourceMap(List<SchemaSource> schemaSources) {
        return schemaSources.stream()
                .map(BraidSchemaSource::new)
                .collect(groupingBy(BraidSchemaSource::getNamespace, singleton()));
    }

    /**
     * This wraps a {@link SchemaSource} to enhance it with helper functions
     */
    private static final class BraidSchemaSource {
        private final SchemaSource schemaSource;
        private final TypeDefinitionRegistry registry;

        private final ObjectTypeDefinition queryType;
        private final ObjectTypeDefinition mutationType;

        private BraidSchemaSource(SchemaSource schemaSource) {
            this.schemaSource = requireNonNull(schemaSource);
            this.registry = schemaSource.getSchema();
            this.queryType = findQueryType(registry).orElse(null);
            this.mutationType = findMutationType(registry).orElse(null);
        }

        SchemaNamespace getNamespace() {
            return schemaSource.getNamespace();
        }

        Collection<? extends TypeDefinition> getNonOperationTypes() {
            return registry.types().values()
                    .stream()
                    .filter(this::isNotOperationType)
                    .collect(toList());
        }

        Optional<ObjectTypeDefinition> getQueryType() {
            return Optional.ofNullable(queryType);
        }

        private Optional<ObjectTypeDefinition> getMutationType() {
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

        private FieldDataLoaderRegistration(String type, String field,
                                            BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader) {
            this.type = type;
            this.field = field;
            this.loader = loader;
        }
    }
}
