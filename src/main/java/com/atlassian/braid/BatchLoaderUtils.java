package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidMaps;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.atlassian.braid.java.util.BraidObjects.cast;

/**
 * A list of utility methods useful when building a new {@link org.dataloader.BatchLoader}
 */
public class BatchLoaderUtils {

    public static String getTargetIdFromEnvironment(Link link, DataFetchingEnvironment environment) {
        return BraidMaps.get(waitForMapSource(environment), link.getSourceFromField()).orElse(null);
    }

    private static Map<String, String> waitForMapSource(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        while (!(source instanceof Map)) {
            if (source instanceof CompletableFuture) {
                try {
                    source = ((CompletableFuture) source).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            } else if (source instanceof DataFetcherResult) {
                source = ((DataFetcherResult) source).getData();
            } else {
                throw new IllegalArgumentException("Unexpected parent type");
            }
        }
        return cast(source);
    }
}
