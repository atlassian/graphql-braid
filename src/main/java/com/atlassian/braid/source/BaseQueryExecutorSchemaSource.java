package com.atlassian.braid.source;

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

final class BaseQueryExecutorSchemaSource<C> extends AbstractSchemaSource implements QueryExecutorSchemaSource {

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
        super(namespace, schema, privateSchema, links);
        this.queryExecutor = new QueryExecutor<>(queryFunction);
        this.documentMapper = requireNonNull(documentMapper);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource, Link link) {
        return queryExecutor.newBatchLoader(schemaSource, link);
    }

    public DocumentMapper getDocumentMapper() {
        return documentMapper.apply(getSchema());
    }
}
