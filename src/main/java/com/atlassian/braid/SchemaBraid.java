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

import static com.atlassian.braid.TypeUtils.createDefaultQueryTypeDefinition;
import static com.atlassian.braid.TypeUtils.createSchemaDefinitionIfNecessary;
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

        final RuntimeWiring.Builder wiringBuilder = config.getRuntimeWiringBuilder();

        final List<BatchLoader> batchLoaders =
                addDataSources(dataSourceTypes, braidTypeRegistry, queryObjectTypeDefinition, wiringBuilder);

        final GraphQLSchema graphQLSchema = new SchemaGenerator()
                .makeExecutableSchema(braidTypeRegistry, wiringBuilder.build());

        return new Braid(graphQLSchema, batchLoaders);
    }

    private List<BatchLoader> addDataSources(Map<SchemaNamespace, Source<C>> dataSources,
                                             TypeDefinitionRegistry registry,
                                             ObjectTypeDefinition queryObjectTypeDefinition,
                                             RuntimeWiring.Builder runtimeWiringBuilder) {
        addAllNonOperationTypes(dataSources, registry);

        final List<BatchLoader> queryFieldsBatchLoaders =
                addSchemaSourcesTopLevelFieldsToQuery(dataSources, queryObjectTypeDefinition, runtimeWiringBuilder);

        final List<BatchLoader> linkedTypesBatchLoaders = linkTypes(dataSources, runtimeWiringBuilder);


        List<BatchLoader> batchLoaders = new LinkedList<>();
        batchLoaders.addAll(queryFieldsBatchLoaders);
        batchLoaders.addAll(linkedTypesBatchLoaders);
        return batchLoaders;
    }

    private void addAllNonOperationTypes(Map<SchemaNamespace, Source<C>> dataSources, TypeDefinitionRegistry registry) {
        dataSources.values().forEach(source -> source.getNonOperationTypes().forEach(registry::add));
    }

    private List<BatchLoader> addSchemaSourcesTopLevelFieldsToQuery(
            Map<SchemaNamespace, Source<C>> sources, ObjectTypeDefinition braidQueryType,
            RuntimeWiring.Builder wiringBuilder) {
        return sources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToQuery(braidQueryType, source, wiringBuilder))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToQuery(
            ObjectTypeDefinition braidQueryType,
            Source<C> source,
            RuntimeWiring.Builder wiringBuilder) {

        return findQueryType(source.registry)
                .map(sourceQueryType -> addSchemaSourceTopLevelFieldsToQuery(braidQueryType, wiringBuilder, sourceQueryType, source.schemaSource))
                .orElse(emptyList());
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToQuery(
            ObjectTypeDefinition braidQueryType,
            RuntimeWiring.Builder wiringBuilder,
            ObjectTypeDefinition sourceQueryType,
            SchemaSource<C> schemaSource) {

        List<BatchLoader> result = new ArrayList<>();

        // todo: smarter merge, optional namespacing, etc
        braidQueryType.getFieldDefinitions().addAll(sourceQueryType.getFieldDefinitions());

        wiringBuilder.type(braidQueryType.getName(), typeWiring -> {
            for (FieldDefinition queryField : sourceQueryType.getFieldDefinitions()) {
                BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> batchLoader = buildBatchLoader(schemaSource, null);
                result.add(batchLoader);
                typeWiring.dataFetcher(queryField.getName(), environment -> {
                            DataLoaderRegistry registry = environment.<BraidContext>getContext().getDataLoaderRegistry();
                            return registry.getDataLoader(batchLoader.toString()).load(environment);
                        }
                );
            }
            return typeWiring;
        });

        return result;
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

                BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> batchLoader = buildBatchLoader(targetSource.schemaSource, link);
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

    private BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> buildBatchLoader(SchemaSource<C> schemaSource, Link link) {
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
