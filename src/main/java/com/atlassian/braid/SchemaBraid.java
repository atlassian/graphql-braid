package com.atlassian.braid;

import graphql.language.Definition;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

/**
 * Weaves data source schemas into a single executable schema
 */
@SuppressWarnings("WeakerAccess")
public class SchemaBraid {

    public static final String QUERY_TYPE_NAME = "Query";
    public static final String QUERY_FIELD_NAME = "query";

    public GraphQLSchema braid(DataSource... dataSources) {
        Map<String, Source> dataSourceTypes = stream(dataSources).collect(toMap(DataSource::getNamespace, Source::new));

        TypeDefinitionRegistry allTypes = new TypeDefinitionRegistry();
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();

        SchemaDefinition schema = new SchemaDefinition();
        allTypes.add(schema);

        allTypes.add(buildQueryOperation(dataSourceTypes, schema, wiringBuilder));

        linkTypes(dataSourceTypes, wiringBuilder).forEach(allTypes::add);

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(allTypes, wiringBuilder.build());

    }

    private Definition buildQueryOperation(Map<String, Source> sources,
                                           SchemaDefinition schema, RuntimeWiring.Builder wiringBuilder) {
        schema.getOperationTypeDefinitions().add(
                new OperationTypeDefinition(QUERY_FIELD_NAME, new TypeName(QUERY_TYPE_NAME)));
        ObjectTypeDefinition query = new ObjectTypeDefinition(QUERY_TYPE_NAME);

        for (Source source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            SchemaDefinition dsSchema = typeRegistry.schemaDefinition().orElseThrow(IllegalArgumentException::new);
            OperationTypeDefinition dsQueryType = dsSchema.getOperationTypeDefinitions().stream().filter(
                    d -> d.getName().equals(QUERY_FIELD_NAME)).findFirst().orElseThrow(IllegalArgumentException::new);
            ObjectTypeDefinition dsQuery = (ObjectTypeDefinition) typeRegistry.getType(dsQueryType.getType())
                    .orElseThrow(IllegalStateException::new);
            HashMap<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());
            dsTypes.remove(dsQuery.getName());

            // todo: smarter merge, optional namespacing, etc
            query.getFieldDefinitions().addAll(dsQuery.getFieldDefinitions());

            wiringBuilder.type(query.getName(), typeWiring -> {
                for (FieldDefinition queryField : dsQuery.getFieldDefinitions()) {
                    typeWiring.dataFetcher(queryField.getName(),
                            environment -> new QueryExecutor().query(source.dataSource, environment, null));
                }
                return typeWiring;
            });
        }
        return query;
    }

    private List<Definition> linkTypes(Map<String, Source> sources, RuntimeWiring.Builder wiringBuilder) {

        List<Definition> result = new ArrayList<>();
        for (Source source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.registry;

            HashMap<String, TypeDefinition> dsTypes = new HashMap<>(typeRegistry.types());

            source.dataSource.getLinks().forEach(link -> {
                // replace the field's type
                ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) dsTypes.get(link.getSourceType());
                FieldDefinition field = typeDefinition.getFieldDefinitions().stream().filter(
                        d -> d.getName().equals(link.getSourceField())).findFirst().orElseThrow(() ->
                        new IllegalArgumentException("Can't find source field: {}" + link.getSourceField()));

                // todo: support different target types like list
                Source targetSource = sources.get(link.getTargetNamespace());
                if (!targetSource.registry.getType(link.getTargetType()).isPresent()) {
                    throw new IllegalArgumentException("Can't find target type: " + link.getTargetType());

                }
                field.setType(new TypeName(link.getTargetType()));

                // wire in the field resolver to the target data source
                wiringBuilder.type(link.getSourceType(), typeWiring -> typeWiring.dataFetcher(link.getSourceField(),
                        environment -> new QueryExecutor().query(targetSource.dataSource, environment, link)));
            });

            // add all types but strip out operation types
            result.addAll(dsTypes.values().stream().filter(d -> !source.operationTypes.contains(d)).collect(Collectors.toList()));
        }
        return result;
    }

    private static class Source {
        private final DataSource dataSource;
        private final TypeDefinitionRegistry registry;
        private final SchemaDefinition schema;
        private final Collection<Definition> operationTypes;

        private Source(DataSource dataSource) {
            this.dataSource = dataSource;

            SchemaParser schemaParser = new SchemaParser();
            this.registry = schemaParser.buildRegistry(dataSource.getSchema());
            this.schema = registry.schemaDefinition().orElseThrow(IllegalArgumentException::new);
            this.operationTypes = schema.getOperationTypeDefinitions().stream()
                    .map(opType -> registry.getType(opType.getType()).orElseThrow(IllegalArgumentException::new))
                    .collect(Collectors.toList());
        }
    }
}
