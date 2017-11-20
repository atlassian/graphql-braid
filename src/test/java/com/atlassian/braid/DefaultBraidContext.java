package com.atlassian.braid;

import org.dataloader.DataLoaderRegistry;

public class DefaultBraidContext implements BraidContext {
    private final DataLoaderRegistry dataLoaderRegistry;

    public DefaultBraidContext(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
    }

    @Override
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }
}
