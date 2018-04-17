package com.atlassian.braid;

import graphql.execution.ExecutionContext;
import org.dataloader.DataLoaderRegistry;

/**
 * <p>Defines the context of Braid GraphQL execution, from which the underlying context can be retrieved.
 * <p>Note: this class is for Braid's internal usage, and should not be used directly. Use methods on
 * {@link BraidContexts} to access context information
 *
 * @see BraidContexts
 */
public interface BraidContext<C> {

    /**
     * @return the data loader registry for this request, since new instances may be created per-request.
     */
    DataLoaderRegistry getDataLoaderRegistry();

    /**
     * @return the {@link ExecutionContext GraphQL execution context} once set
     * @throws IllegalStateException if the method is called too early, i.e. the execution context is not (yet) available
     */
    ExecutionContext getExecutionContext();

    /**
     * @return the underlying user set context
     */
    C getContext();
}
