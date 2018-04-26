package com.atlassian.braid.source.yaml;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves maps from a remote REST service.  Meant to be used with {@link RestRemoteSchemaSource}.
 *
 * Typically implementations of this class will need to deal with overall request configuration i.e. authentication
 * as the url will be built and supplied by the {@link RestRemoteSchemaSource}.
 */
public interface RestRemoteRetriever<C> {

    /**
     * @param url the processed URL with all templated parameters pre bound
     * @param context the context
     * @return the response body of the query
     */
    CompletableFuture<Map<String, Object>> get(URL url, C context);
}
