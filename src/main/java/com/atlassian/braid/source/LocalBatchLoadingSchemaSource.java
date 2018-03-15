package com.atlassian.braid.source;

import com.atlassian.braid.BatchLoaderFactory;
import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import java.io.Reader;
import java.util.List;
import java.util.function.Supplier;

import static com.atlassian.braid.source.SchemaUtils.loadPublicSchema;
import static com.atlassian.braid.source.SchemaUtils.loadSchema;
import static java.util.Objects.requireNonNull;

public final class LocalBatchLoadingSchemaSource<C extends BraidContext> extends AbstractSchemaSource<C> implements SchemaSource<C> {

    private final BatchLoaderFactory<C> batchLoaderFactory;

    public LocalBatchLoadingSchemaSource(SchemaNamespace namespace,
                                         Supplier<Reader> schemaProvider,
                                         List<Link> links,
                                         BatchLoaderFactory<C> batchLoaderFactory,
                                         String... topLevelFields) {
        super(namespace, loadPublicSchema(schemaProvider, topLevelFields), loadSchema(schemaProvider), links);
        this.batchLoaderFactory = requireNonNull(batchLoaderFactory);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return batchLoaderFactory.newBatchLoader(schemaSource, link);
    }
}
