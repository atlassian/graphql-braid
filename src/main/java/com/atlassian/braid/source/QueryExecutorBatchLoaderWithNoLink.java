package com.atlassian.braid.source;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import java.util.List;
import java.util.concurrent.CompletionStage;

final class QueryExecutorBatchLoaderWithNoLink<C> implements BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> {

    private final QueryExecutorSchemaSource schemaSource;
    private final QueryFunction<C> queryFunction;

    QueryExecutorBatchLoaderWithNoLink(QueryExecutorSchemaSource schemaSource, QueryFunction<C> queryFunction) {
        this.schemaSource = schemaSource;
        this.queryFunction = queryFunction;
    }

    @Override
    public CompletionStage<List<DataFetcherResult<Object>>> load(List<DataFetchingEnvironment> keys) {



        return null;
    }
}
