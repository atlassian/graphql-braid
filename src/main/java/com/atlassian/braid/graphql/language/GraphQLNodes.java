package com.atlassian.braid.graphql.language;

import graphql.language.Node;

/**
 * Utility class to work with GraphQL nodes
 */
public final class GraphQLNodes {
    private GraphQLNodes() {
    }

    public static String printNode(Node node) {
        return new GraphQLQueryPrinter().print(node);
    }
}
