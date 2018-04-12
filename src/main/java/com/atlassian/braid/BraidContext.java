package com.atlassian.braid;

import org.dataloader.DataLoaderRegistry;

import java.util.Map;

/**
 * <p>Defines the context of Braid GraphQL execution, from which the underlying context can be retrieved.
 * <p>Note: this class is for Braid's internal usage, and should not be used directly. Use methods on
 * {@link BraidContexts} to access context information
 *
 * @see BraidContexts
 */
public interface BraidContext<C> {

    BraidExecutionContext getExecutionContext();

    C getContext();

    interface BraidExecutionContext {

        /**
         * @return the data loader registry for this request, since new instances may be created per-request.
         */
        DataLoaderRegistry getDataLoaderRegistry();

        /**
         * @return the original variables
         */
        Map<String, Object> getVariables();

        /**
         * @return the original query
         */
        String getQuery();
    }
}
