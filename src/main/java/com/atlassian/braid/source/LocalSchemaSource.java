package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Local schema source
 */
@SuppressWarnings("WeakerAccess")
public final class LocalSchemaSource<C> implements SchemaSource<C> {
    private final SchemaNamespace namespace;
    private final TypeDefinitionRegistry schema;
    private final List<Link> links;
    private final Function<ExecutionInput, Object> queryExecutor;

    public LocalSchemaSource(SchemaNamespace namespace,
                             TypeDefinitionRegistry schema,
                             Function<ExecutionInput, Object> queryExecutor) {
        this(namespace, schema, emptyList(), queryExecutor);
    }

    public LocalSchemaSource(SchemaNamespace namespace,
                             TypeDefinitionRegistry schema,
                             List<Link> links,
                             Function<ExecutionInput, Object> queryExecutor) {
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
    public SchemaNamespace getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput executionInput, C context) {
        final Object result = queryExecutor.apply(transformExecutionInput(executionInput, context));
        if (result instanceof DataFetcherResult) {
            return completedFuture((cast(result)));
        } else if (result instanceof Map) {
            return completedFuture(new DataFetcherResult<>(cast(result), emptyList()));
        } else {
            CompletableFuture<DataFetcherResult<Map<String, Object>>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Unexpected result type: " + nullSafeGetClass(result)));
            return future;
        }
    }

    private ExecutionInput transformExecutionInput(ExecutionInput executionInput, C context) {
        return executionInput.transform(builder -> builder.context(context));
    }

    private static Class<?> nullSafeGetClass(Object result) {
        return Optional.ofNullable(result).map(Object::getClass).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}
