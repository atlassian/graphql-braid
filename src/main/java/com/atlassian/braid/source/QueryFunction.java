package com.atlassian.braid.source;

import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface QueryFunction<C> {

    CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput input, C context);
}