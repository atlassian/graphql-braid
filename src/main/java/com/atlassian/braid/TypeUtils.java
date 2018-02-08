package com.atlassian.braid;

import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.atlassian.braid.SchemaBraid.QUERY_FIELD_NAME;
import static java.util.Arrays.asList;

/**
 * Utils for helping to navigate types
 */
public class TypeUtils {
    /**
     * Finds the query type definition.
     *
     * @param registry all types
     * @return the query type
     * @throws IllegalArgumentException if the query definition can't be found or there is no schema
     */

    static ObjectTypeDefinition findQueryType(TypeDefinitionRegistry registry) throws IllegalArgumentException {
        return registry.schemaDefinition()
                .map(SchemaDefinition::getOperationTypeDefinitions)
                .flatMap(TypeUtils::findQueryOperationDefinition)
                .map(OperationTypeDefinition::getType)
                .flatMap(registry::getType)
                .map(ObjectTypeDefinition.class::cast)
                .orElseThrow(IllegalArgumentException::new);
    }

    private static Optional<OperationTypeDefinition> findQueryOperationDefinition(List<OperationTypeDefinition> definitions) {
        return definitions.stream()
                .filter(d -> Objects.equals(d.getName(), QUERY_FIELD_NAME))
                .findFirst();
    }

    /**
     * Filters the top level fields on a query just to the provided list.  If no fields specified, no action is taken.
     *
     * @param registry       the types
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
