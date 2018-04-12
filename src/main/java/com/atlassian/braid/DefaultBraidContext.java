package com.atlassian.braid;

import org.dataloader.DataLoaderRegistry;

import java.util.Map;

final class DefaultBraidContext<C> implements BraidContext<C> {
    private final BraidContext.BraidExecutionContext braidExecutionContext;
    private final C context; // nullable

    DefaultBraidContext(DataLoaderRegistry dataLoaderRegistry, Map<String, Object> variables, String query, C context) {
        this.braidExecutionContext = new DefaultBraidExecutionContext(dataLoaderRegistry, variables, query);
        this.context = context;
    }

    @Override
    public BraidExecutionContext getExecutionContext() {
        return braidExecutionContext;
    }

    @Override
    public C getContext() {
        return context;
    }

    private static final class DefaultBraidExecutionContext implements BraidContext.BraidExecutionContext {
        private final DataLoaderRegistry dataLoaderRegistry;
        private final Map<String, Object> variables;
        private final String query;

        private DefaultBraidExecutionContext(DataLoaderRegistry dataLoaderRegistry, Map<String, Object> variables, String query) {
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
}
