package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaSource;
import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;
import graphql.language.Document;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Local schema source
 */
@SuppressWarnings("WeakerAccess")
public class LocalSchemaSource<C> implements SchemaSource<C> {
    private final String namespace;
    private final TypeDefinitionRegistry schema;
    private final List<Link> links;
    private final Function<ExecutionInput, Object> queryExecutor;

    public LocalSchemaSource(String namespace, TypeDefinitionRegistry schema, Function<ExecutionInput, Object> queryExecutor) {
        this(namespace, schema, emptyList(), queryExecutor);
    }

    public LocalSchemaSource(String namespace, TypeDefinitionRegistry schema, List<Link> links, Function<ExecutionInput, Object> queryExecutor) {
        this.namespace = requireNonNull(namespace);
        this.schema = requireNonNull(schema);
        this.links = requireNonNull(links);
        this.queryExecutor = requireNonNull(queryExecutor);
    }

    @Override
    public TypeDefinitionRegistry getSchema() {
        return schema;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput query, C context) {
        Object result = queryExecutor.apply(query);
        if (result instanceof DataFetcherResult) {
            return CompletableFuture.completedFuture((DataFetcherResult<Map<String, Object>>)result);
        } else if (result instanceof Map) {
            return CompletableFuture.completedFuture(new DataFetcherResult<>((Map<String, Object>)result, emptyList()));
        } else {
            CompletableFuture<DataFetcherResult<Map<String, Object>>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException());
            return future;
        }
    }
}
