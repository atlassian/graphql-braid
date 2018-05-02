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
import java.util.function.Predicate;

import static com.atlassian.braid.java.util.BraidOptionals.firstNonEmpty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Utils for helping to navigate types
 */
public final class TypeUtils {

    public static final String QUERY_FIELD_NAME = "query";
    public static final String MUTATION_FIELD_NAME = "mutation";

    public static final String DEFAULT_QUERY_TYPE_NAME = "Query";
    public static final String DEFAULT_MUTATION_TYPE_NAME = "Mutation";

    private TypeUtils() {
    }

    /**
     * Creates an <em>emtpy</em> schema definition if the registry doesn't have one already
     *
     * @param registry the registry to complete
     * @return the registry
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
     * @param registry the type registry to complete
     * @return the created query type definition
     */
    static ObjectTypeDefinition createDefaultQueryTypeDefinition(TypeDefinitionRegistry registry) {
        return createOperationTypeDefinition(registry, QUERY_FIELD_NAME, DEFAULT_QUERY_TYPE_NAME);
    }

    /**
     * Creates a default mutation type definition and adds it to the given registry
     *
     * @param registry the type registry to complete
     * @return the created query type definition
     */
    static ObjectTypeDefinition createDefaultMutationTypeDefinition(TypeDefinitionRegistry registry) {
        return createOperationTypeDefinition(registry, MUTATION_FIELD_NAME, DEFAULT_MUTATION_TYPE_NAME);
    }

    private static ObjectTypeDefinition createOperationTypeDefinition(TypeDefinitionRegistry registry,
                                                                      String operationFieldName,
                                                                      String operationTypeName) {
        registry.schemaDefinition()
                .orElseThrow(IllegalStateException::new) // by now the schema definition should have been created
                .getOperationTypeDefinitions()
                .add(new OperationTypeDefinition(operationFieldName, new TypeName(operationTypeName)));

        final ObjectTypeDefinition mutationObjectTypeDefinition = new ObjectTypeDefinition(operationTypeName);
        registry.add(mutationObjectTypeDefinition);

        return mutationObjectTypeDefinition;
    }

    /**
     * Finds the query field definitions
     *
     * @param registry the type registry to look into
     * @return the optional query fields definitions
     */
    public static Optional<List<FieldDefinition>> findQueryFieldDefinitions(TypeDefinitionRegistry registry) {
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
        return firstNonEmpty(
                () -> findOperationType(registry, TypeUtils::isQueryOperation),
                () -> getObjectTypeDefinitionByName(registry, DEFAULT_QUERY_TYPE_NAME));
    }


    /**
     * Finds the query type definition.
     *
     * @param registry the type registry to look into
     * @return the optional query type
     */

    static Optional<ObjectTypeDefinition> findMutationType(TypeDefinitionRegistry registry) throws IllegalArgumentException {
        return firstNonEmpty(
                () -> findOperationType(registry, TypeUtils::isMutationOperation),
                () -> getObjectTypeDefinitionByName(registry, DEFAULT_MUTATION_TYPE_NAME));
    }

    private static Optional<ObjectTypeDefinition> findOperationType(TypeDefinitionRegistry registry, Predicate<OperationTypeDefinition> isQueryOperation) {
        return findOperationDefinitions(registry)
                .flatMap(definitions -> findOperationDefinition(definitions, isQueryOperation))
                .flatMap(getObjectTypeDefinition(registry));
    }

    static List<ObjectTypeDefinition> findOperationTypes(TypeDefinitionRegistry registry) {
        return findOperationDefinitions(registry)
                .map(toObjectTypeDefinition(registry))
                .orElse(emptyList());
    }

    public static Optional<List<OperationTypeDefinition>> findOperationDefinitions(TypeDefinitionRegistry registry) {
        return registry.schemaDefinition()
                .map(SchemaDefinition::getOperationTypeDefinitions);
    }


    private static Optional<OperationTypeDefinition> findOperationDefinition(List<OperationTypeDefinition> definitions,
                                                                             Predicate<OperationTypeDefinition> isOperation) {
        return definitions.stream()
                .filter(isOperation)
                .findFirst();
    }

    private static boolean isQueryOperation(OperationTypeDefinition operationTypeDefinition) {
        return Objects.equals(operationTypeDefinition.getName(), QUERY_FIELD_NAME);
    }

    private static boolean isMutationOperation(OperationTypeDefinition operationTypeDefinition) {
        return Objects.equals(operationTypeDefinition.getName(), MUTATION_FIELD_NAME);
    }

    /**
     * Filters the top level fields on a query just to the provided list.  If no fields specified, no action is taken.
     *
     * @param registry       the types
     * @param topLevelFields the fields to allow or if empty, all of them
     * @return the registry passed as a parameter, updated
     */
    public static TypeDefinitionRegistry filterQueryType(TypeDefinitionRegistry registry, String... topLevelFields) {
        List<String> topFields = asList(topLevelFields);
        if (!topFields.isEmpty()) {
            findQueryType(registry).map(ObjectTypeDefinition::getFieldDefinitions)
                    .ifPresent(d -> d.removeIf(field -> !topFields.contains(field.getName())));
        }
        return registry;
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

    private static Optional<ObjectTypeDefinition> getObjectTypeDefinitionByName(TypeDefinitionRegistry registry, String typeName) {
        return registry.getType(typeName).map(ObjectTypeDefinition.class::cast);
    }
}
