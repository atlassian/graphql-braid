package com.atlassian.braid.document;

import com.atlassian.braid.document.FieldOperation.FieldOperationResult;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.findOperationDefinitions;
import static com.atlassian.braid.document.FieldOperation.result;
import static com.atlassian.braid.document.Fields.findObjectTypeDefinition;
import static com.atlassian.braid.mapper.Mappers.mapper;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

final class TypedDocumentMapper implements DocumentMapper {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;

    TypedDocumentMapper(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
    }

    @Override
    public MappedDocument apply(Document input) {
        final Document output = new Document();

        final MapperOperation reduce = getOperationDefinitionStream(input)
                .map(d -> {
                    final ObjectTypeDefinition operationTypeDefinition = findOperationTypeDefinition(schema, d);

                    final List<Selection> outputSelections = new ArrayList<>();

                    final Map<Boolean, List<Selection>> fieldsAndNonFields =
                            d.getSelectionSet().getSelections().stream().collect(groupingBy(s -> s instanceof Field));

                    // take care of all non-Field selection
                    outputSelections.addAll(fieldsAndNonFields.getOrDefault(false, emptyList()));

                    final MapperOperation mapper =
                            BraidObjects.<List<Field>>cast(fieldsAndNonFields.getOrDefault(true, emptyList()))
                                    .stream()
                                    .map(BraidObjects::<Field>cast)
                                    .map(field -> getMappingContext(operationTypeDefinition, field))
                                    .map(TypedDocumentMapper::mapNode)
                                    .peek(or -> or.getField().ifPresent(outputSelections::add))
                                    .map(FieldOperationResult::getMapper)
                                    .reduce(MapperOperation::andThen)
                                    .orElse(MapperOperations.noop());

                    final SelectionSetMappingResult mapped =
                            new SelectionSetMappingResult(new SelectionSet(outputSelections), mapper);

                    output.getDefinitions().add(newOperationDefinition(d, mapped));
                    return mapped.getResultMapper();
                })
                .reduce(MapperOperation::andThen)
                .orElse(MapperOperations.noop());

        return new MappedDocument(output, mapper(reduce));
    }

    private MappingContext getMappingContext(ObjectTypeDefinition operationTypeDefinition, Field field) {
        return MappingContext.of(
                schema,
                typeMappers,
                findObjectTypeDefinition(schema, operationTypeDefinition, field),
                field);
    }

    static FieldOperationResult mapNode(MappingContext mappingContext) {
        final ObjectTypeDefinition definition = mappingContext.getObjectTypeDefinition();
        final Field field = mappingContext.getField();

        return mappingContext.getTypeMappers()
                .stream()
                .filter(typeMapper -> typeMapper.test(definition))
                .findFirst()
                .map(typeMapper -> typeMapper.apply(mappingContext, field.getSelectionSet()))
                .map(mappingResult -> mappingResult.toFieldOperationResult(field))
                .orElse(result(field));
    }

    private static Stream<OperationDefinition> getOperationDefinitionStream(Document input) {
        return input.getDefinitions().stream()
                .filter(d -> d instanceof OperationDefinition)
                .map(OperationDefinition.class::cast);
    }

    private OperationDefinition newOperationDefinition(OperationDefinition original,
                                                       SelectionSetMappingResult selectionSetMappingResult) {
        return new OperationDefinition(
                original.getName(),
                original.getOperation(),
                original.getVariableDefinitions(),
                original.getDirectives(),
                selectionSetMappingResult.getSelectionSet());
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
}
