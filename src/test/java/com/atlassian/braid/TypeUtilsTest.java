package com.atlassian.braid;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.atlassian.braid.TypeUtils.findQueryType;
import static com.atlassian.braid.Util.parseRegistry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TypeUtilsTest {

    @Test
    public void filterQueryType() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        TypeUtils.filterQueryType(registry, "foo");

        Optional<ObjectTypeDefinition> queryType = findQueryType(registry);
        assertThat(queryType).isPresent();

        final List<FieldDefinition> queryFieldDefinitions = queryType
                .map(ObjectTypeDefinition::getFieldDefinitions)
                .orElseThrow(IllegalStateException::new);

        assertThat(queryFieldDefinitions).hasSize(1);
        assertThat(queryFieldDefinitions).extracting("name").containsExactly("foo");
    }

    @Test
    public void filterQueryTypeButKeepAllFields() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        TypeUtils.filterQueryType(registry);

        Optional<ObjectTypeDefinition> queryType = findQueryType(registry);
        assertThat(queryType).isPresent();

        final List<FieldDefinition> queryFieldDefinitions = queryType
                .map(ObjectTypeDefinition::getFieldDefinitions)
                .orElseThrow(IllegalStateException::new);

        assertThat(queryFieldDefinitions).hasSize(2);
    }
}
