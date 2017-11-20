package com.atlassian.braid;

import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;

import static com.atlassian.braid.SchemaBraid.QUERY_FIELD_NAME;
import static java.util.Arrays.asList;

/**
 * Utils for helping to navigate types
 */
public class TypeUtils {
    /**
     * Finds the query type definition.
     * @param registry all types
     * @return the query type
     * @throws IllegalArgumentException if the query definition can't be found or there is no schema
     */

    static ObjectTypeDefinition findQueryType(TypeDefinitionRegistry registry) throws IllegalArgumentException {
        SchemaDefinition dsSchema = registry.schemaDefinition().orElseThrow(IllegalArgumentException::new);
        OperationTypeDefinition dsQueryType = dsSchema.getOperationTypeDefinitions().stream()
                .filter(d -> d.getName().equals(QUERY_FIELD_NAME))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
        return (ObjectTypeDefinition) registry.getType(dsQueryType.getType())
                .orElseThrow(IllegalStateException::new);
    }

    /**
     * Filters the top level fields on a query just to the provided list.  If no fields specified, no action is taken.
     * @param registry the types
     * @param topLevelFields the fields to allow or if empty, all of them
     */
    public static void filterQueryType(TypeDefinitionRegistry registry, String... topLevelFields) {
        List<String> topFields = asList(topLevelFields);
        if (!topFields.isEmpty()) {
            ObjectTypeDefinition dsQuery = findQueryType(registry);
            dsQuery.getFieldDefinitions().removeIf(field -> !topFields.contains(field.getName()));
        }
    }
}
