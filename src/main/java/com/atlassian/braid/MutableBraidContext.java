package com.atlassian.braid;

import graphql.execution.ExecutionContext;
import org.dataloader.DataLoaderRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

import static com.atlassian.braid.java.util.BraidPreconditions.checkState;
import static java.util.Objects.requireNonNull;

final class MutableBraidContext<C> implements BraidContext<C> {
    private final AtomicReference<ExecutionContext> executionContext;
    private final DataLoaderRegistry dataLoaderRegistry;

    @Nullable
    private final C context;

    MutableBraidContext(@Nonnull DataLoaderRegistry dataLoaderRegistry, @Nullable C context) {
        this.executionContext = new AtomicReference<>();
        this.dataLoaderRegistry = requireNonNull(dataLoaderRegistry);
        this.context = context;
    }

    @Override
    public ExecutionContext getExecutionContext() {
        final ExecutionContext context = this.executionContext.get();
        checkState(context != null,
                "Method called too early, before the execution context had a chance to be set");
        return context;
    }

    ExecutionContext setExecutionContext(ExecutionContext newExecutionContext) {
        this.executionContext.set(newExecutionContext);
        return newExecutionContext;
    }

    @Override
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }

    @Override
    public C getContext() {
        return context;
    }
}
