package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidMaps;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * A list of utility methods useful when building a new {@link org.dataloader.BatchLoader}
 */
public class BatchLoaderUtils {

    public static List getTargetIdsFromEnvironment(Link link, DataFetchingEnvironment environment) {
        Object ids = waitForMapSource(environment, link.getSourceFromField()).orElse(null);
        if (ids instanceof String || ids instanceof Number) {
            return singletonList(ids);
        } else if (ids instanceof List) {
            return (List) ids;
        } else if (ids == null) {
            if (environment.getFieldType() instanceof GraphQLList) {
                return emptyList();
            } else {
                return singletonList(null);
            }
        } else {
            throw new IllegalArgumentException("Unexpected id type: " + ids);
        }
    }

    private static Optional<Object> waitForMapSource(DataFetchingEnvironment environment, String fromField) {
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
            } else if (source instanceof String || source instanceof Number || source instanceof List) {
                return Optional.of(source);
            } else {
                throw new IllegalArgumentException("Unexpected parent type");
            }
        }
        return BraidMaps.get(cast(source), fromField);
    }
}
