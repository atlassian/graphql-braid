package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.atlassian.braid.TypeUtils.filterQueryType;
import static com.atlassian.braid.source.OptionalHelper.castNullableList;
import static com.atlassian.braid.source.OptionalHelper.castNullableMap;

/**
 * Data source for an external graphql service.  Loads the schema on construction.
 */
@SuppressWarnings("WeakerAccess")
public class GraphQLRemoteSchemaSource<C> implements SchemaSource<C> {

    private final SchemaNamespace namespace;
    private final GraphQLRemoteRetriever<C> graphQLRemoteRetriever;
    private final List<Link> links;
    private final TypeDefinitionRegistry publicSchema;
    private final TypeDefinitionRegistry privateSchema;

    public GraphQLRemoteSchemaSource(SchemaNamespace namespace,
                                     Supplier<Reader> schemaProvider,
                                     GraphQLRemoteRetriever<C> graphQLRemoteRetriever,
                                     List<Link> links,
                                     String... topLevelFields) {
        this.namespace = namespace;
        this.graphQLRemoteRetriever = graphQLRemoteRetriever;
        this.links = links;

        TypeDefinitionRegistry schema = loadSchema(schemaProvider);
        filterQueryType(schema, topLevelFields);
        this.publicSchema = schema;
        this.privateSchema = loadSchema(schemaProvider);
    }

    @Override
    public TypeDefinitionRegistry getSchema() {
        return publicSchema;
    }

    @Override
    public TypeDefinitionRegistry getPrivateSchema() {
        return privateSchema;
    }

    @Override
    public SchemaNamespace getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput query, C context) {
        return graphQLRemoteRetriever.queryGraphQL(query, context).thenApply(response -> {

            Map<String, Object> data = castNullableMap(response.get("data"), String.class, Object.class)
                    .orElse(Collections.emptyMap());
            final List<Map> errorsMap = castNullableList(response.get("errors"), Map.class)
                    .orElse(Collections.emptyList());

            List<GraphQLError> errors = errorsMap.stream()
                    .map(val -> (GraphQLError) new MapGraphQLError(
                            castNullableMap(val, String.class, Object.class).orElseThrow(IllegalArgumentException::new)))
                    .collect(Collectors.toList());
            return new DataFetcherResult<>(data, errors);
        });
    }

    private TypeDefinitionRegistry loadSchema(Supplier<Reader> schema) {
        SchemaParser parser = new SchemaParser();
        return parser.parse(schema.get());
    }
}
