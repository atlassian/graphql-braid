package com.atlassian.braid;

import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Represents a braided schema and its loaders
 */
@SuppressWarnings("WeakerAccess")
public class Braid {
    private final GraphQLSchema schema;
    private final List<BatchLoader> batchLoaders;

    public Braid(GraphQLSchema schema, List<BatchLoader> batchLoaders) {
        this.schema = requireNonNull(schema);
        this.batchLoaders = requireNonNull(batchLoaders);
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public DataLoaderRegistry newDataLoaderRegistry() {
        DataLoaderRegistry registry = new DataLoaderRegistry();
        for (BatchLoader loader : batchLoaders) {
            registry.register(loader.toString(), new DataLoader(loader));
        }
        return registry;
    }
}
