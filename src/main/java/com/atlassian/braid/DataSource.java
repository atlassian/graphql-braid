package com.atlassian.braid;

import graphql.ExecutionInput;
import graphql.language.Document;

import java.util.List;

/**
 * A data source that contains a schema to be merged via the weaver
 */
public interface DataSource {

    /**
     * @return the schema document
     */
    Document getSchema();

    /**
     * @return the data source identifier to be used in links targeting this data source
     */
    String getNamespace();

    /**
     * @return a list of links that connect fields in this data source to other data sources
     */
    List<Link> getLinks();

    /**
     * @param query the query to execute
     */
    Object query(ExecutionInput query);
}
