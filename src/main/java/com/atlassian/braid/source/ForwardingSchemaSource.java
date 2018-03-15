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

abstract class ForwardingSchemaSource<C extends BraidContext> implements SchemaSource<C> {
    protected abstract SchemaSource<C> getDelegate();

    @Override
    public TypeDefinitionRegistry getSchema() {
        return getDelegate().getSchema();
    }

    @Override
    public TypeDefinitionRegistry getPrivateSchema() {
        return getDelegate().getPrivateSchema();
    }

    @Override
    public SchemaNamespace getNamespace() {
        return getDelegate().getNamespace();
    }

    @Override
    public List<Link> getLinks() {
        return getDelegate().getLinks();
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return getDelegate().newBatchLoader(schemaSource, link);
    }
}
