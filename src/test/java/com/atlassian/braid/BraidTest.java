package com.atlassian.braid;

import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    public void testBraidWithInterface() {
        assertThat(rule.braid.getSchema().getObjectType("Foo")
                .getInterfaces().get(0).getName()).isEqualTo("Fooable");
    }
}
