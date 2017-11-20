package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaSource;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.atlassian.braid.TypeUtils.filterQueryType;
import static com.atlassian.braid.source.OptionalHelper.castNullableList;
import static com.atlassian.braid.source.OptionalHelper.castNullableMap;

/**
 * Data source for an external graphql service.  Loads the schema from the external service on
 * construction.
 */
@SuppressWarnings("WeakerAccess")
public class RemoteSchemaSource<C> implements SchemaSource<C> {

    private static final Logger log = LoggerFactory.getLogger(RemoteSchemaSource.class);

    private final String namespace;
    private final RemoteRetriever<C> remoteRetriever;
    private final List<Link> links;
    private final TypeDefinitionRegistry schema;

    public RemoteSchemaSource(String namespace, RemoteRetriever<C> remoteRetriever, List<Link> links, String... topLevelFields) {
        this.namespace = namespace;
        this.remoteRetriever = remoteRetriever;
        this.links = links;

        try {
            TypeDefinitionRegistry schema = new SchemaParser().buildRegistry(loadSchema().get());
            filterQueryType(schema, topLevelFields);
            this.schema = schema;
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TypeDefinitionRegistry getSchema() {
        return schema;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput query, C context) {
        return remoteRetriever.query(query, context).thenApply(response -> {

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

    private CompletableFuture<Document> loadSchema() throws IOException {
        return remoteRetriever.queryIntrospectionSchema().thenApply(response ->
                castNullableMap(response.get("data"), String.class, Object.class)
                        .map(data -> new IntrospectionResultToSchema().createSchemaDefinition(data))
                        .orElseThrow(IllegalArgumentException::new));
    }
}
