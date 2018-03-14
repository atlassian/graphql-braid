package com.atlassian.braid.graphql.instrumenation;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.language.Field;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions.newOptions;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * This is a braid specific data loader, that handles efficient batch loading.
 * <p>
 * <strong>Note:</strong> the base implementation of the loader is
 * {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 */
public final class BraidDataLoaderDispatcherInstrumentation extends NoOpInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(BraidDataLoaderDispatcherInstrumentation.class);

    private final DataLoaderRegistry dataLoaderRegistry;
    private final DataLoaderDispatcherInstrumentationOptions options;

    /**
     * You pass in a registry of N data loaders which will be {@link DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param dataLoaderRegistry the registry of data loaders that will be dispatched
     */
    public BraidDataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry) {
        this(dataLoaderRegistry, newOptions());
    }

    /**
     * You pass in a registry of N data loaders which will be {@link DataLoader#dispatch() dispatched} as
     * each level of the query executes.
     *
     * @param dataLoaderRegistry the registry of data loaders that will be dispatched
     * @param options            the options to control the behaviour
     */
    public BraidDataLoaderDispatcherInstrumentation(DataLoaderRegistry dataLoaderRegistry, DataLoaderDispatcherInstrumentationOptions options) {
        this.dataLoaderRegistry = requireNonNull(dataLoaderRegistry);
        this.options = requireNonNull(options);
    }

    @Override
    public InstrumentationState createState() {
        return new BraidDispatcherInstrumentationState();
    }

    private void dispatch() {
        log.debug("Dispatching data loaders ({})", dataLoaderRegistry.getKeys());
        dataLoaderRegistry.dispatchAll();
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginDataFetchDispatch(InstrumentationDataFetchParameters parameters) {
        return onEndDispatch(this::dispatch);
    }

    @Override
    public InstrumentationContext<Map<String, List<Field>>> beginFields(InstrumentationExecutionStrategyParameters parameters) {
        final BraidDispatcherInstrumentationState state = parameters.getInstrumentationState();

        if (!state.fieldsEnteredOnce()) {
            state.enterFields();
            return onEndDispatchIfNeeded(this::dispatch, state);
        } else {
            return onEndNoop();
        }
    }

    /*
       When graphql-java enters a field list it re-cursively called the execution strategy again, which will cause an early flush
       to the data loader - which is not efficient from a batch point of view.  We want to allow the list of field values
       to bank up as promises and call dispatch when we are clear of a list value.

       https://github.com/graphql-java/graphql-java/issues/760
     */
    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
        BraidDispatcherInstrumentationState braidDispatcherInstrumentationState = parameters.getInstrumentationState();
        braidDispatcherInstrumentationState.enterList();

        return composed(
                (__, ___) -> braidDispatcherInstrumentationState.exitList(),
                onEndDispatchIfNeeded(this::dispatch, braidDispatcherInstrumentationState));
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        if (!options.isIncludeStatistics()) {
            return CompletableFuture.completedFuture(executionResult);
        }
        Map<Object, Object> currentExt = executionResult.getExtensions();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.putAll(currentExt == null ? Collections.emptyMap() : currentExt);
        Map<Object, Object> dataLoaderStats = buildStatsMap();
        statsMap.put("dataloader", dataLoaderStats);

        log.debug("Data loader stats : {}", dataLoaderStats);

        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), statsMap));
    }

    private Map<Object, Object> buildStatsMap() {
        Statistics allStats = dataLoaderRegistry.getStatistics();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("overall-statistics", allStats.toMap());

        Map<Object, Object> individualStatsMap = new LinkedHashMap<>();

        for (String dlKey : dataLoaderRegistry.getKeys()) {
            DataLoader<Object, Object> dl = dataLoaderRegistry.getDataLoader(dlKey);
            Statistics statistics = dl.getStatistics();
            individualStatsMap.put(dlKey, statistics.toMap());
        }

        statsMap.put("individual-statistics", individualStatsMap);

        return statsMap;
    }

    private static <T> InstrumentationContext<T> onEndNoop() {
        return (__, ___) -> {
        };
    }

    private static <T> InstrumentationContext<T> onEndDispatch(Runnable dispatcher) {
        return new DispatchIfNeededInstrumentationContext<>(dispatcher, () -> true);
    }

    private static <T> InstrumentationContext<T> onEndDispatchIfNeeded(Runnable dispatcher, BraidDispatcherInstrumentationState state) {
        return new DispatchIfNeededInstrumentationContext<>(dispatcher, () -> !state.isInList());
    }

    @SafeVarargs
    private static <T> InstrumentationContext<T> composed(InstrumentationContext<T>... contexts) {
        return new ComposedInstrumentationContext<>(asList(contexts));
    }

    private static class ComposedInstrumentationContext<T> implements InstrumentationContext<T> {
        private final List<InstrumentationContext<T>> contexts;

        private ComposedInstrumentationContext(List<InstrumentationContext<T>> contexts) {
            this.contexts = requireNonNull(contexts);
        }

        @Override
        public void onEnd(T result, Throwable t) {
            contexts.forEach(c -> c.onEnd(result, t));
        }
    }

    private static class DispatchIfNeededInstrumentationContext<T> implements InstrumentationContext<T> {
        private final Supplier<Boolean> shouldDispatch;
        private final Runnable dispatcher;

        private DispatchIfNeededInstrumentationContext(Runnable dispatcher, Supplier<Boolean> shouldDispatch) {
            this.shouldDispatch = requireNonNull(shouldDispatch);
            this.dispatcher = requireNonNull(dispatcher);
        }

        @Override
        public void onEnd(T result, Throwable t) {
            if (shouldDispatch.get()) {
                dispatcher.run();
            }
        }
    }

    /**
     * We need to become stateful about whether we are in a list or not
     */
    private static final class BraidDispatcherInstrumentationState implements InstrumentationState {
        private final Deque<Boolean> listTracker = new ArrayDeque<>();

        private final AtomicBoolean fieldsEnteredOnce = new AtomicBoolean();

        private void enterFields() {
            fieldsEnteredOnce.set(true);
        }

        private boolean fieldsEnteredOnce() {
            return fieldsEnteredOnce.get();
        }

        private void enterList() {
            synchronized (this) {
                listTracker.push(true);
            }
        }

        private void exitList() {
            synchronized (this) {
                listTracker.poll();
            }
        }

        private boolean isInList() {
            synchronized (this) {
                return !listTracker.isEmpty() ? listTracker.peek() : false;
            }
        }

        @Override
        public String toString() {
            return "isInList=" + isInList() + ", fieldsEnteredOnce=" + fieldsEnteredOnce();
        }
    }
}