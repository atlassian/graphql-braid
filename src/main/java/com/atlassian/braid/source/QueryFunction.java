package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface QueryFunction<C extends BraidContext> {

    CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput input, C context);
}