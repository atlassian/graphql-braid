package com.atlassian.braid;

import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import java.io.IOException;

import static com.atlassian.braid.TypeUtils.findQueryType;
import static com.atlassian.braid.Util.parseRegistry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TypeUtilsTest {

    @Test
    public void filterQueryType() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        TypeUtils.filterQueryType(registry, "foo");

        ObjectTypeDefinition queryType = findQueryType(registry);
        assertThat(queryType.getFieldDefinitions().size()).isEqualTo(1);
        assertThat(queryType.getFieldDefinitions().get(0).getName()).isEqualTo("foo");
    }

    @Test
    public void filterQueryTypeButKeepAllFields() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        TypeUtils.filterQueryType(registry);

        ObjectTypeDefinition queryType = findQueryType(registry);
        assertThat(queryType.getFieldDefinitions().size()).isEqualTo(2);
    }
}
