package com.atlassian.braid.source;

import graphql.ExecutionInput;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves maps from a remote service.  Meant to be used with {@link RemoteSchemaSource}.
 */
public interface RemoteRetriever<C> {

    /**
     * @return The response body of an introspection query
     */
    CompletableFuture<Map<String, Object>> queryIntrospectionSchema();

    /**
     * @param executionInput the query to execute
     * @return the response body of the query
     */
    CompletableFuture<Map<String, Object>> query(ExecutionInput executionInput, C context);
}
