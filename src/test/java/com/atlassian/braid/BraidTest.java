package com.atlassian.braid;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import org.junit.Rule;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BraidTest {

    @Rule
    public YamlBraidExecutionRule rule = new YamlBraidExecutionRule();

    @Test
    public void testBraidWithMutation() {
    }

    @Test
    public void testBraidWithMutationAndObjectInput() {
    }

    @Test
    public void testBraidWithMutationAndTwoSchemaSources() {
    }

    @Test
    public void testBraidWithMultipleSameAliasedQueries() {
    }

    @Test
    public void testBraidWithExtraVariables() {
    }

    @Test
    public void testBraidWithTwoSchemaSources() {
    }

    @Test
    public void testBraidWithSchemaSourceError() {
    }

    @Test
    public void testBraidWithInlineVariables() {
    }

    @Test
    public void testBraidWithFragment() {
    }

    @Test
    public void testBraidWithFragmentAndVariables() {
    }

    @Test
    public void testBraidWithMultipleNodesUsingFragment() {
    }

    @Test
    public void testBraidWithNamedOperation() {
    }

    @Test
    public void testBraidWithTypename() {
    }

    @Test
    public void testBraidWithLinkFromSiblingField() {
    }

    @Test
    public void testBraidWithLinkFromSiblingFieldButNoFromFieldInQuery() {
    }

    @Test
    public void testBraidWithLinkOnlyQueryingID() {
    }

    @Test
    public void testBraidBatchingWithLink() {
    }

    @Test
    public void testBraidWithLinkFromReplaceField() {
        Optional<GraphQLType> fooType = rule.braid.getSchema().getAllTypesAsList().stream()
                .filter(t -> t.getName().equals("Foo")).findAny();
        assertEquals(true, fooType.isPresent());
        assertTrue(fooType.get() instanceof GraphQLObjectType);
        GraphQLObjectType foo = (GraphQLObjectType) fooType.get();
        Optional<GraphQLFieldDefinition> idField = foo.getFieldDefinitions().stream()
                .filter(f -> f.getName().equals("id")).findAny();
        assertEquals(false, idField.isPresent());
    }

    @Test
    public void testBraidWithNullFromField() {
    }

    @Test
    public void testBraidWithNullFromFieldWithNullSupport() {
    }

    @Test
    public void testBraidWithInterface() {
        assertThat(rule.braid.getSchema().getObjectType("Foo")
                .getInterfaces().get(0).getName()).isEqualTo("Fooable");
    }
}
