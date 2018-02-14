package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class QueryExecutorSchemaSource<C extends BraidContext> extends AbstractSchemaSource<C> {

    private final QueryExecutor<C> queryExecutor;

    public QueryExecutorSchemaSource(SchemaNamespace namespace,
                                     TypeDefinitionRegistry schema,
                                     List<Link> links,
                                     QueryFunction<C> queryFunction) {
        this(namespace, schema, schema, links, queryFunction);
    }

    public QueryExecutorSchemaSource(SchemaNamespace namespace,
                                     TypeDefinitionRegistry schema,
                                     TypeDefinitionRegistry privateSchema,
                                     List<Link> links,
                                     QueryFunction<C> queryFunction) {
        this(namespace, schema, privateSchema, links, new QueryExecutor<C>(queryFunction));

    }

    private QueryExecutorSchemaSource(SchemaNamespace namespace,
                                      TypeDefinitionRegistry schema,
                                      TypeDefinitionRegistry privateSchema,
                                      List<Link> links,
                                      QueryExecutor<C> queryExecutor) {
        super(namespace, schema, privateSchema, links);
        this.queryExecutor = Objects.requireNonNull(queryExecutor);

    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return queryExecutor.newBatchLoader(schemaSource, link);
    }
}
