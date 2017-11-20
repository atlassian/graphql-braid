package com.atlassian.braid;

import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A data source that contains a schema to be merged via {@link SchemaBraid}
 */
public interface SchemaSource<C> {

    /**
     * @return the schema document
     */
    TypeDefinitionRegistry getSchema();

    /**
     * @return the data source identifier to be used in links targeting this data source.
     * {@see Link#getTargetNamespace()}
     */
    String getNamespace();

    /**
     * @return a list of links that connect fields in this data source to other data sources
     */
    List<Link> getLinks();

    /**
     * @param query the query to execute
     */
    CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput query, C context);
}
