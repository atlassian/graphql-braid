package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;

import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.atlassian.braid.source.OptionalHelper.castNullableList;
import static com.atlassian.braid.source.OptionalHelper.castNullableMap;
import static com.atlassian.braid.source.SchemaUtils.loadPublicSchema;
import static com.atlassian.braid.source.SchemaUtils.loadSchema;
import static java.util.Objects.requireNonNull;

/**
 * Data source for an external graphql service.  Loads the schema on construction.
 */
@SuppressWarnings("WeakerAccess")
public final class GraphQLRemoteSchemaSource<C extends BraidContext> extends ForwardingSchemaSource<C> {

    private final QueryExecutorSchemaSource<C> delegate;
    private final GraphQLRemoteRetriever<C> graphQLRemoteRetriever;


    public GraphQLRemoteSchemaSource(SchemaNamespace namespace,
                                     Supplier<Reader> schemaProvider,
                                     GraphQLRemoteRetriever<C> graphQLRemoteRetriever,
                                     List<Link> links,
                                     String... topLevelFields) {
        this.graphQLRemoteRetriever = requireNonNull(graphQLRemoteRetriever);
        this.delegate = new QueryExecutorSchemaSource<>(namespace,
                loadPublicSchema(schemaProvider, topLevelFields),
                loadSchema(schemaProvider),
                links,
                this::query);
    }

    @Override
    protected SchemaSource<C> getDelegate() {
        return delegate;
    }

    // visible for testing
    CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput query, C context) {
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
}
