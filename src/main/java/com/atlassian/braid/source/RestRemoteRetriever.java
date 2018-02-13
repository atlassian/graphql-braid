package com.atlassian.braid.source;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves maps from a remote REST service.  Meant to be used with {@link RestRemoteSchemaSource}.
 */
public interface RestRemoteRetriever<C> {

    /**
     * @param url the processed URL
     * @param context the context
     * @return the response body of the query
     */
    CompletableFuture<Map<String, Object>> get(URL url, C context);
}
