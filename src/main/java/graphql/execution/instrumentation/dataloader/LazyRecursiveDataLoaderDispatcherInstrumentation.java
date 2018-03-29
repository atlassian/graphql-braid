package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionResult;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.impl.CompletableFutureKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.atlassian.braid.java.util.BraidLists.concat;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenDispatched;
import static java.util.Objects.requireNonNull;

/**
 * Batch loading instrumentation that aims at being as lazy as possible and dispatches recursively when needed to ensure
 * a future generating new futures eventually finishes.
 */
public final class LazyRecursiveDataLoaderDispatcherInstrumentation extends SimpleInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(LazyRecursiveDataLoaderDispatcherInstrumentation.class);

    private final DataLoaderRegistry dataLoaderRegistry;

    public LazyRecursiveDataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = requireNonNull(dataLoaderRegistry);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return whenDispatched(__ -> dispatch());
    }

    private void dispatch() {
        log.debug("Dispatching all data loaders ({})", dataLoaderRegistry.getKeys());
        final DispatchBatchLoaderCalls allDispatched = dataLoaderRegistry.getKeys().stream()
                .map(key -> dispatchBatchLoader(dataLoaderRegistry, key))
                .reduce(new DispatchBatchLoaderCalls(), DispatchBatchLoaderCalls::add, DispatchBatchLoaderCalls::combine);

        if (allDispatched.depth > 0) {
            allDispatched.whenComplete(this::dispatch);
        }
    }

    private DispatchBatchLoaderCall dispatchBatchLoader(DataLoaderRegistry dataLoaderRegistry, String key) {
        final DataLoader<Object, DataFetcherResult> dataLoader = dataLoaderRegistry.getDataLoader(key);

        final int dispatchDepth = dataLoader.dispatchDepth();
        return new DispatchBatchLoaderCall<>(dispatchDepth, dataLoader.dispatch());
    }

    private static class DispatchBatchLoaderCall<V> {
        private final int depth;
        private final CompletableFuture<List<V>> futures;

        private DispatchBatchLoaderCall(int depth, CompletableFuture<List<V>> futures) {
            this.depth = depth;
            this.futures = futures;
        }
    }

    private static class DispatchBatchLoaderCalls {
        private final int depth;
        private final List<CompletableFuture<List<?>>> futures;

        private DispatchBatchLoaderCalls() {
            this(0);
        }

        private DispatchBatchLoaderCalls(int depth) {
            this.depth = depth;
            this.futures = new ArrayList<>();
        }

        private DispatchBatchLoaderCalls(int depth, List<CompletableFuture<List<?>>> futures) {
            this.depth = depth;
            this.futures = new ArrayList<>(futures);
        }

        @SuppressWarnings("unchecked")
        private DispatchBatchLoaderCalls add(DispatchBatchLoaderCall dbl) {
            return new DispatchBatchLoaderCalls(this.depth + dbl.depth, concat(this.futures, dbl.futures));
        }

        private static DispatchBatchLoaderCalls combine(DispatchBatchLoaderCalls ds1, DispatchBatchLoaderCalls ds2) {
            return new DispatchBatchLoaderCalls(ds1.depth + ds2.depth, concat(ds1.futures, ds2.futures));
        }

        private void whenComplete(Runnable onComplete) {
            CompletableFutureKit.allOf(futures).whenComplete((__, ___) -> onComplete.run());
        }
    }
}
