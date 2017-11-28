package com.atlassian.braid;

import org.dataloader.DataLoaderRegistry;

import java.util.Map;

public class DefaultBraidContext implements BraidContext {
    private final DataLoaderRegistry dataLoaderRegistry;
    private final Map<String, Object> variables;
    private final String query;

    DefaultBraidContext(DataLoaderRegistry dataLoaderRegistry, Map<String, Object> variables, String query) {
        this.dataLoaderRegistry = dataLoaderRegistry;
        this.variables = variables;
        this.query = query;
    }

    @Override
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public String getQuery() {
        return query;
    }
}
