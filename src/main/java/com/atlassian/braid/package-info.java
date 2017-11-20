/**
 * Package that implements schema weaving much like
 * <a href="https://github.com/AEB-labs/graphql-weaver">graphql-weaver</a>.  This feature differs from
 * atlassian-graphql-java's router by support merged schemas at any depth, not just root nodes.  Also, graphql-braid
 * operates within a single {@link graphql.GraphQL} instance instead of being a layer on top.
 * <p>
 * Supported features:<ul>
 * <li>Ability to present a single schema to consumers and have it be executed through multiple GraphQL service
 * calls behind the scenes</li>
 * <li>Ability to link a field in a type to resolve against another data source anywhere in the graph</li>
 * <li>Queries to underlying GraphQL only request what fields the user requested</li>
 * <li>Named fragements and variables passed along</li>
 * </ul>
 * <p>
 * Missing features:<ul>
 * <li>Support for inline fragments</li>
 * <li>Support for links that aren't one-to-one.  That includes one-to-many or joins</li>
 * <li>Advanced type mapping, collision handling, and type transformations</li>
 * <li>Batching</li>
 * <li>Support for operations other than query</li>
 * <li>Lots more things undiscovered...</li>
 * </ul>
 */
package com.atlassian.braid;