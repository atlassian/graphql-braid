package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import javax.annotation.Nullable;

/**
 * Builds a new {@link BatchLoader} instance for a source and optional link
 */
public interface BatchLoaderFactory {

    /**
     * Builds a new batch loader
     *
     * @param schemaSource the schema source
     * @param link         the link, may be null
     * @return a new loader instance
     */
    BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource,
                                                                                   @Nullable Link link);
}
