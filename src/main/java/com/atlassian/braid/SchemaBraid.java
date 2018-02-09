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
import java.util.Optional;

import static com.atlassian.braid.TypeUtils.createDefaultMutationTypeDefinition;
import static com.atlassian.braid.TypeUtils.createDefaultQueryTypeDefinition;
import static com.atlassian.braid.TypeUtils.createSchemaDefinitionIfNecessary;
import static com.atlassian.braid.TypeUtils.findMutationType;
import static com.atlassian.braid.TypeUtils.findOperationTypes;
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

    private final QueryExecutor queryExecutor;

    public SchemaBraid() {
        this(new QueryExecutor());
    }

    SchemaBraid(QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

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
                addSchemaSourcesTopLevelFieldsToQuery(dataSources, runtimeWiringBuilder, queryObjectTypeDefinition);

        final List<BatchLoader> mutationFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToMutation(dataSources, runtimeWiringBuilder, mutationObjectTypeDefinition);

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

    private List<BatchLoader> addSchemaSourcesTopLevelFieldsToQuery(Map<SchemaNamespace, Source<C>> dataSources,
                                                                    RuntimeWiring.Builder runtimeWiringBuilder,
                                                                    ObjectTypeDefinition braidQueryType) {
        return dataSources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToQuery(source, runtimeWiringBuilder, braidQueryType))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private List<BatchLoader> addSchemaSourcesTopLevelFieldsToMutation(Map<SchemaNamespace, Source<C>> dataSources,
                                                                       RuntimeWiring.Builder runtimeWiringBuilder,
                                                                       ObjectTypeDefinition braidQueryType) {
        return dataSources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToMutation(source, runtimeWiringBuilder, braidQueryType))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToMutation(
            Source<C> source, RuntimeWiring.Builder wiringBuilder, ObjectTypeDefinition braidQueryType) {

        return findMutationType(source.registry)
                .map(sourceQueryType -> addSchemaSourceTopLevelFieldsToMutation(source.schemaSource, wiringBuilder, braidQueryType, sourceQueryType))
                .orElse(emptyList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToQuery(
            Source<C> source, RuntimeWiring.Builder wiringBuilder, ObjectTypeDefinition braidQueryType) {

        return findQueryType(source.registry)
                .map(sourceQueryType -> addSchemaSourceTopLevelFieldsToQuery(source.schemaSource, wiringBuilder, braidQueryType, sourceQueryType))
                .orElse(emptyList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToQuery(
            SchemaSource<C> schemaSource,
            RuntimeWiring.Builder runtimeWiringBuilder,
            ObjectTypeDefinition queryType,
            ObjectTypeDefinition sourceQueryType) {

        final List<BatchLoader> result = new ArrayList<>();

        // todo: smarter merge, optional namespacing, etc
        queryType.getFieldDefinitions().addAll(sourceQueryType.getFieldDefinitions());

        runtimeWiringBuilder.type(queryType.getName(),
                typeRuntimeWiringBuilder -> {
                    result.addAll(wireQueryFields(typeRuntimeWiringBuilder, schemaSource, sourceQueryType));
                    return typeRuntimeWiringBuilder;
                });

        return result;
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToMutation(
            SchemaSource<C> schemaSource,
            RuntimeWiring.Builder runtimeWiringBuilder,
            ObjectTypeDefinition queryType,
            ObjectTypeDefinition sourceQueryType) {

        final List<BatchLoader> result = new ArrayList<>();

        // todo: smarter merge, optional namespacing, etc
        queryType.getFieldDefinitions().addAll(sourceQueryType.getFieldDefinitions());

        runtimeWiringBuilder.type(queryType.getName(),
                typeRuntimeWiringBuilder -> {
                    result.addAll(wireMutationFields(typeRuntimeWiringBuilder, schemaSource, sourceQueryType));
                    return typeRuntimeWiringBuilder;
                });

        return result;
    }

    private List<BatchLoader> wireMutationFields(TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                                 SchemaSource<C> schemaSource,
                                                 ObjectTypeDefinition sourceQueryType) {
        return sourceQueryType.getFieldDefinitions().stream()
                .map(queryField -> wireMutationField(typeRuntimeWiringBuilder, schemaSource, queryField))
                .collect(toList());
    }

    private BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> wireMutationField(
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

    private List<BatchLoader> wireQueryFields(TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
                                              SchemaSource<C> schemaSource,
                                              ObjectTypeDefinition sourceQueryType) {
        return sourceQueryType.getFieldDefinitions().stream()
                .map(queryField -> wireQueryField(typeRuntimeWiringBuilder, schemaSource, queryField))
                .collect(toList());
    }

    private BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> wireQueryField(
            TypeRuntimeWiring.Builder typeRuntimeWiringBuilder,
            SchemaSource<C> schemaSource,
            FieldDefinition queryField) {

        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> batchLoader =
                newBatchLoader(schemaSource, null);

        typeRuntimeWiringBuilder.dataFetcher(queryField.getName(), environment -> {
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

                Optional<FieldDefinition> sourceField = typeDefinition.getFieldDefinitions().stream().filter(
                        d -> d.getName().equals(link.getSourceField())).findFirst();

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

    private BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        // We use DataFetchingEnvironment as the key in the BatchLoader because different fetches of the object may
        // request different fields. Someday we may smartly combine them into one somehow, but that day isn't today.
        if (schemaSource instanceof BatchLoaderFactory) {
            return ((BatchLoaderFactory) schemaSource).newBatchLoader(schemaSource, link);
        } else {
            return queryExecutor.newBatchLoader(schemaSource, link);
        }
    }

    private void validateSourceFromFieldExists(Link link, ObjectTypeDefinition typeDefinition) {
        //noinspection ResultOfMethodCallIgnored
        typeDefinition.getFieldDefinitions().stream().filter(
                d -> d.getName().equals(link.getSourceFromField()))
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

    private static final class Source<C> {
        private final SchemaSource<C> schemaSource;
        private final TypeDefinitionRegistry registry;
        private final Collection<? extends TypeDefinition> operationTypes;

        private Source(SchemaSource<C> schemaSource) {
            this.schemaSource = requireNonNull(schemaSource);
            this.registry = schemaSource.getSchema();
            this.operationTypes = findOperationTypes(registry);
        }

        Collection<? extends TypeDefinition> getNonOperationTypes() {
            return registry.types().values()
                    .stream()
                    .filter(this::isNotOperationType)
                    .collect(toList());
        }

        boolean isNotOperationType(TypeDefinition typeDefinition) {
            return !isOperationType(typeDefinition);
        }

        boolean isOperationType(TypeDefinition typeDefinition) {
            return operationTypes.contains(typeDefinition);
        }
    }
}
