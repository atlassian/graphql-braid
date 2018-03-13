package com.atlassian.braid.source;

import graphql.ExecutionInput;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves maps from a remote service.  Meant to be used with {@link GraphQLRemoteSchemaSource}.
 */
public interface GraphQLRemoteRetriever<C> {

    /**
     * @param executionInput the query to execute
     * @param context        the GraphQL execution context
     * @return the response body of the query
     */
    CompletableFuture<Map<String, Object>> queryGraphQL(ExecutionInput executionInput, C context);
}
