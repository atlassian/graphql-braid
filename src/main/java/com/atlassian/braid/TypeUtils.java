package com.atlassian.braid;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Utils for helping to navigate types
 */
public class TypeUtils {

    private static final String DEFAULT_QUERY_TYPE_NAME = "Query";
    private static final String QUERY_FIELD_NAME = "query";

    /**
     * Creates an <em>emtpy</em> schema definition if the registry doesn't have one already
     *
     * @param registry the registry to complete
     */
    static TypeDefinitionRegistry createSchemaDefinitionIfNecessary(TypeDefinitionRegistry registry) {
        if (!registry.schemaDefinition().isPresent()) {
            registry.add(new SchemaDefinition());
        }
        return registry;
    }

    /**
     * Creates a default query type definition and adds it to the given registry
     *
     * @param typeRegistry the type registry to complete
     * @return the created query type definition
     */
    static ObjectTypeDefinition createDefaultQueryTypeDefinition(TypeDefinitionRegistry typeRegistry) {
        OperationTypeDefinition queryOperationTypeDefinition =
                new OperationTypeDefinition(QUERY_FIELD_NAME, new TypeName(DEFAULT_QUERY_TYPE_NAME));

        typeRegistry.schemaDefinition()
                .orElseThrow(IllegalStateException::new) // by now the schema definition should have been created
                .getOperationTypeDefinitions()
                .add(queryOperationTypeDefinition);

        final ObjectTypeDefinition queryObjectTypeDefinition = new ObjectTypeDefinition(DEFAULT_QUERY_TYPE_NAME);
        typeRegistry.add(queryObjectTypeDefinition);

        return queryObjectTypeDefinition;
    }

    /**
     * Finds the query field definitions
     *
     * @param registry the type registry to look into
     * @return the optional query fields definitions
     */
    static Optional<List<FieldDefinition>> findQueryFieldDefinitions(TypeDefinitionRegistry registry) {
        return findQueryType(registry)
                .map(ObjectTypeDefinition::getFieldDefinitions);
    }

    /**
     * Finds the query type definition.
     *
     * @param registry the type registry to look into
     * @return the optional query type
     */

    static Optional<ObjectTypeDefinition> findQueryType(TypeDefinitionRegistry registry) throws IllegalArgumentException {
        return findOperationDefinitions(registry)
                .flatMap(TypeUtils::findQueryOperationDefinition)
                .flatMap(getObjectTypeDefinition(registry));
    }

    static List<ObjectTypeDefinition> findOperationTypes(TypeDefinitionRegistry registry) {
        return findOperationDefinitions(registry)
                .map(toObjectTypeDefinition(registry))
                .orElse(emptyList());
    }

    private static Optional<List<OperationTypeDefinition>> findOperationDefinitions(TypeDefinitionRegistry registry) {
        return registry.schemaDefinition()
                .map(SchemaDefinition::getOperationTypeDefinitions);
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
            findQueryType(registry).map(ObjectTypeDefinition::getFieldDefinitions)
                    .ifPresent(d -> d.removeIf(field -> !topFields.contains(field.getName())));
        }
    }

    private static Function<List<OperationTypeDefinition>, List<ObjectTypeDefinition>> toObjectTypeDefinition(TypeDefinitionRegistry registry) {
        return ods -> ods.stream()
                .map(getObjectTypeDefinition(registry))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    private static Function<OperationTypeDefinition, Optional<ObjectTypeDefinition>> getObjectTypeDefinition(
            TypeDefinitionRegistry registry) {
        return otd -> registry.getType(otd.getType()).map(ObjectTypeDefinition.class::cast);
    }
}
