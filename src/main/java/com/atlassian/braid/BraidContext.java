package com.atlassian.braid;

import org.dataloader.DataLoaderRegistry;

/**
 * Marks that context that provides access to the {@link DataLoaderRegistry}
 */
public interface BraidContext {

    /**
     * @return the data loader registry for this request, since new instances may be created per-request.
     */
    DataLoaderRegistry getDataLoaderRegistry();
}
