package com.atlassian.braid.document;

import com.atlassian.braid.document.FieldOperation.FieldOperationResult;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.Selection;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.findOperationDefinitions;
import static com.atlassian.braid.document.FieldOperation.result;
import static com.atlassian.braid.document.MappedOperations.toMappedDocument;
import static com.atlassian.braid.document.OperationMappingResult.toOperationMappingResult;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

/**
 * <strong>Internal</strong> implementation of the {@link DocumentMapper} that maps based on types
 * using {@link TypeMapper type mappers}
 *
 * @see TypeMapper
 */
final class TypedDocumentMapper implements DocumentMapper {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;

    TypedDocumentMapper(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
    }

    @Override
    public MappedDocument apply(Document input) {
        return getOperationDefinitionStream(input)
                .map(this::mapOperation)
                .collect(toMappedDocument());
    }

    private OperationMappingResult mapOperation(OperationDefinition operationDefinition) {
        final ObjectTypeDefinition operationTypeDefinition = findOperationTypeDefinition(schema, operationDefinition);
        final Map<Boolean, List<Selection>> fieldsAndNonFields = getFieldsAndNonFields(operationDefinition);

        final List<Selection> nonFields = fieldsAndNonFields.getOrDefault(false, emptyList());
        final List<Field> fields = cast(fieldsAndNonFields.getOrDefault(true, emptyList()));

        return fields.stream()
                .map(field -> toMappingContext(operationTypeDefinition, field))
                .map(TypedDocumentMapper::mapNode)
                .collect(toOperationMappingResult(operationDefinition, nonFields));
    }

    private MappingContext toMappingContext(ObjectTypeDefinition parentObjectType, Field field) {
        return MappingContext.of(schema, typeMappers, parentObjectType, field);
    }

    static FieldOperationResult mapNode(MappingContext mappingContext) {
        final ObjectTypeDefinition definition = mappingContext.getObjectTypeDefinition();
        final Field field = mappingContext.getField();

        return mappingContext.getTypeMappers()
                .stream()
                .filter(typeMapper -> typeMapper.test(definition))
                .findFirst()
                .map(typeMapper -> typeMapper.apply(mappingContext, field.getSelectionSet()))
                .map(mappingResult -> mappingResult.toFieldOperationResult(mappingContext))
                .orElse(result(field));
    }

    private static Stream<OperationDefinition> getOperationDefinitionStream(Document input) {
        return input.getDefinitions().stream()
                .filter(d -> d instanceof OperationDefinition)
                .map(OperationDefinition.class::cast);
    }

    private static ObjectTypeDefinition findOperationTypeDefinition(TypeDefinitionRegistry schema, OperationDefinition op) {
        return findOperationDefinitions(schema)
                .flatMap(maybeFindOperationTypeDefinition(op))
                .map(OperationTypeDefinition::getType)
                .flatMap(schema::getType)
                .map(BraidObjects::<ObjectTypeDefinition>cast)
                .orElseThrow(IllegalStateException::new);
    }

    private static Function<List<OperationTypeDefinition>, Optional<OperationTypeDefinition>> maybeFindOperationTypeDefinition(OperationDefinition op) {
        return ops -> ops.stream().filter(isOperationTypeDefinitionForOperationType(op)).findFirst();
    }

    private static Predicate<OperationTypeDefinition> isOperationTypeDefinitionForOperationType(OperationDefinition op) {
        return otd -> otd.getName().equalsIgnoreCase(op.getOperation().name());
    }

    /**
     * Groups {@link Selection selections} by type, the key {@code true} for those of type {@link Field}, {@code false}
     * for any other type
     *
     * @param operationDefinition the operation definition for which we care about fields
     * @return a map of {@link Field fields} and <em>other</em> {@link Selection selection types}
     */
    private static Map<Boolean, List<Selection>> getFieldsAndNonFields(OperationDefinition operationDefinition) {
        return operationDefinition.getSelectionSet().getSelections()
                .stream()
                .collect(groupingBy(s -> s instanceof Field));
    }
}
