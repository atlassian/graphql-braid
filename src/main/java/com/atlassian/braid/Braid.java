package com.atlassian.braid;

import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Represents a braided schema and its loaders
 */
@SuppressWarnings("WeakerAccess")
public class Braid {
    private final GraphQLSchema schema;
    private final Map<String, BatchLoader> batchLoaders;

    public Braid(GraphQLSchema schema, Map<String, BatchLoader> batchLoaders) {
        this.schema = requireNonNull(schema);
        this.batchLoaders = requireNonNull(batchLoaders);
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public DataLoaderRegistry newDataLoaderRegistry() {
        DataLoaderRegistry registry = new DataLoaderRegistry();
        batchLoaders.forEach((key, loader) -> registry.register(key, new DataLoader(loader)));
        return registry;
    }
}
