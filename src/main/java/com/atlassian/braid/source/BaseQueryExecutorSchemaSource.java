package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.document.DocumentMapper;
import com.atlassian.braid.document.DocumentMapperFactory;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;

import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

final class BaseQueryExecutorSchemaSource<C extends BraidContext> extends AbstractSchemaSource<C> implements QueryExecutorSchemaSource<C> {

    private final QueryExecutor<C> queryExecutor;
    private final Function<TypeDefinitionRegistry, DocumentMapper> documentMapper;

    BaseQueryExecutorSchemaSource(SchemaNamespace namespace,
                                  TypeDefinitionRegistry schema,
                                  List<Link> links,
                                  DocumentMapperFactory documentMapper,
                                  QueryFunction<C> queryFunction) {
        this(namespace, schema, schema, links, documentMapper, queryFunction);
    }

    BaseQueryExecutorSchemaSource(SchemaNamespace namespace,
                                  TypeDefinitionRegistry schema,
                                  TypeDefinitionRegistry privateSchema,
                                  List<Link> links,
                                  DocumentMapperFactory documentMapper,
                                  QueryFunction<C> queryFunction) {
        this(namespace, schema, privateSchema, links, documentMapper, new QueryExecutor<C>(queryFunction));
    }

    private BaseQueryExecutorSchemaSource(SchemaNamespace namespace,
                                          TypeDefinitionRegistry schema,
                                          TypeDefinitionRegistry privateSchema,
                                          List<Link> links,
                                          Function<TypeDefinitionRegistry, DocumentMapper> documentMapper,
                                          QueryExecutor<C> queryExecutor) {
        super(namespace, schema, privateSchema, links);
        this.queryExecutor = requireNonNull(queryExecutor);
        this.documentMapper = requireNonNull(documentMapper);

    }


    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return queryExecutor.newBatchLoader(schemaSource, link);
    }

    public DocumentMapper getDocumentMapper() {
        return documentMapper.apply(getSchema());
    }
}
