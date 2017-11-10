package com.atlassian.braid;

import graphql.ExecutionInput;
import graphql.language.Document;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Local data source
 */
@SuppressWarnings("WeakerAccess")
public class LocalDataSource implements DataSource {
    private final String namespace;
    private final Document schema;
    private final List<Link> links;
    private final Function<ExecutionInput, Object> queryExecutor;

    public LocalDataSource(String namespace, Document schema, Function<ExecutionInput, Object> queryExecutor) {
        this(namespace, schema, Collections.emptyList(), queryExecutor);
    }

    public LocalDataSource(String namespace, Document schema, List<Link> links, Function<ExecutionInput, Object> queryExecutor) {
        this.namespace = namespace;
        this.schema = schema;
        this.links = links;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Document getSchema() {
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
    public Object query(ExecutionInput query) {
        return queryExecutor.apply(query);
    }
}
