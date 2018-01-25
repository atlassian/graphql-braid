package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import java.util.Map;

/**
 * Builds a new {@link BatchLoader} instance for a source and optional link
 */
public interface BatchLoaderFactory {
    /**
     * Builds a new batch loader
     * @param schemaSource the schema source
     * @param link the link, may be null
     * @param <C> the context type
     * @return a new loader instance
     */
    <C extends BraidContext> BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(
            SchemaSource<C> schemaSource,
            Link link);
}
