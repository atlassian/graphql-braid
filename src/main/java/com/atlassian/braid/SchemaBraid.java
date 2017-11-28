package com.atlassian.braid;

import graphql.language.Definition;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Weaves source schemas into a single executable schema
 */
@SuppressWarnings("WeakerAccess")
public class SchemaBraid<C extends BraidContext> {

    public static final String QUERY_TYPE_NAME = "Query";
    public static final String QUERY_FIELD_NAME = "query";

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

    public Braid braid(SchemaBraidConfiguration<C> configuration) {
        Map<SchemaNamespace, Source<C>> dataSourceTypes = configuration.getSchemaSources().stream()
                .collect(toMap(SchemaSource::getNamespace, Source::new));

        TypeDefinitionRegistry allTypes = configuration.getTypeDefinitionRegistry();
        SchemaDefinition schema = allTypes.schemaDefinition().orElseGet(() -> {
            SchemaDefinition s = new SchemaDefinition();
            allTypes.add(s);
            return s;
        });

        ObjectTypeDefinition query = schema.getOperationTypeDefinitions().stream()
                .filter(d -> d.getName().equals(QUERY_FIELD_NAME))
                .findFirst()
                .map(operType -> (ObjectTypeDefinition) allTypes.getType(operType.getType()).orElseThrow(IllegalArgumentException::new))
                .orElseGet(() -> {
                    OperationTypeDefinition definition = new OperationTypeDefinition(QUERY_FIELD_NAME, new TypeName(QUERY_TYPE_NAME));
                    schema.getOperationTypeDefinitions().add(definition);
                    ObjectTypeDefinition queryType = new ObjectTypeDefinition(QUERY_TYPE_NAME);
                    allTypes.add(queryType);
                    return queryType;
                });

        RuntimeWiring.Builder wiringBuilder = configuration.getRuntimeWiringBuilder();
        List<BatchLoader> queryBatchLoaders = addSchemaSourceTopLevelFieldsToQuery(query, dataSourceTypes, wiringBuilder);
        List<BatchLoader> linkBatchLoaders = linkTypes(allTypes, dataSourceTypes, wiringBuilder);

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return new Braid(schemaGenerator.makeExecutableSchema(allTypes, wiringBuilder.build()), Stream.concat(
                queryBatchLoaders.stream(),
                linkBatchLoaders.stream()
        ).collect(Collectors.toList()));
    }

    private List<BatchLoader> addSchemaSourceTopLevelFieldsToQuery(
            ObjectTypeDefinition query,
            Map<SchemaNamespace, Source<C>> sources,
            RuntimeWiring.Builder wiringBuilder) {
        List<BatchLoader> result = new ArrayList<>();
        for (Source<C> source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            SchemaDefinition dsSchema = typeRegistry.schemaDefinition().orElseThrow(IllegalArgumentException::new);
            OperationTypeDefinition dsQueryType = dsSchema.getOperationTypeDefinitions().stream()
                    .filter(d -> d.getName().equals(QUERY_FIELD_NAME))
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new);
            ObjectTypeDefinition dsQuery = (ObjectTypeDefinition) typeRegistry.getType(dsQueryType.getType())
                    .orElseThrow(IllegalStateException::new);
            HashMap<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());
            dsTypes.remove(dsQuery.getName());

            // todo: smarter merge, optional namespacing, etc
            query.getFieldDefinitions().addAll(dsQuery.getFieldDefinitions());

            wiringBuilder.type(query.getName(), typeWiring -> {
                for (FieldDefinition queryField : dsQuery.getFieldDefinitions()) {
                    BatchLoader<DataFetchingEnvironment, Object> batchLoader = queryExecutor.asBatchLoader(source.schemaSource, null);
                    result.add(batchLoader);
                    typeWiring.dataFetcher(queryField.getName(), environment -> {
                                DataLoaderRegistry registry = environment.<BraidContext>getContext().getDataLoaderRegistry();
                                return registry.getDataLoader(batchLoader.toString()).load(environment);
                            }
                    );

                }
                return typeWiring;
            });
        }
        return result;
    }

    private List<BatchLoader> linkTypes(TypeDefinitionRegistry allTypes, Map<SchemaNamespace, Source<C>> sources, RuntimeWiring.Builder wiringBuilder) {

        List<Definition> definitionsToAdd = new ArrayList<>();
        List<BatchLoader> batchLoaders = new ArrayList<>();
        for (Source<C> source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            HashMap<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());

            for (Link link : source.schemaSource.getLinks()) {
                // replace the field's type
                ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) dsTypes.get(link.getSourceType());
                FieldDefinition field = typeDefinition.getFieldDefinitions().stream().filter(
                        d -> d.getName().equals(link.getSourceField())).findFirst().orElseThrow(() ->
                        new IllegalArgumentException("Can't find source field: {}" + link.getSourceField()));

                // todo: support different target types like list
                Source<C> targetSource = sources.get(link.getTargetNamespace());
                if (!targetSource.registry.getType(link.getTargetType()).isPresent()) {
                    throw new IllegalArgumentException("Can't find target type: " + link.getTargetType());

                }
                field.setType(new TypeName(link.getTargetType()));

                BatchLoader<DataFetchingEnvironment, Object> batchLoader = queryExecutor.asBatchLoader(targetSource.schemaSource, link);
                batchLoaders.add(batchLoader);

                // wire in the field resolver to the target data source
                wiringBuilder.type(link.getSourceType(), typeWiring -> typeWiring.dataFetcher(link.getSourceField(), environment -> {
                            DataLoaderRegistry registry = environment.<BraidContext>getContext().getDataLoaderRegistry();
                            return registry.getDataLoader(batchLoader.toString()).load(environment);
                        }
                ));
            }

            // add all types but strip out operation types
            definitionsToAdd.addAll(dsTypes.values().stream().filter(d -> !source.operationTypes.contains(d)).collect(Collectors.toList()));
        }
        definitionsToAdd.forEach(allTypes::add);
        return batchLoaders;
    }

    private static class Source<C> {
        private final SchemaSource<C> schemaSource;
        private final TypeDefinitionRegistry registry;
        private final SchemaDefinition schema;
        private final Collection<Definition> operationTypes;

        private Source(SchemaSource<C> schemaSource) {
            this.schemaSource = schemaSource;

            this.registry = schemaSource.getSchema();
            this.schema = registry.schemaDefinition().orElseThrow(IllegalArgumentException::new);
            this.operationTypes = schema.getOperationTypeDefinitions().stream()
                    .map(opType -> registry.getType(opType.getType()).orElseThrow(IllegalArgumentException::new))
                    .collect(Collectors.toList());
        }
    }
}
