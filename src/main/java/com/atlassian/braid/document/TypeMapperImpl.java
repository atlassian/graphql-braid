package com.atlassian.braid.document;

import com.atlassian.braid.document.FieldOperation.FieldOperationResult;
import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import com.atlassian.braid.source.OptionalHelper;
import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.atlassian.braid.java.util.BraidLists.concat;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * <strong>Internal</strong> implementation of the {@link TypeMapper}
 *
 * @see TypeMapper
 */
final class TypeMapperImpl implements TypeMapper {

    private final Predicate<ObjectTypeDefinition> predicate;
    private final List<FieldOperation> fieldOperations;

    TypeMapperImpl(Predicate<ObjectTypeDefinition> predicate) {
        this(predicate, emptyList());
    }

    TypeMapperImpl(Predicate<ObjectTypeDefinition> predicate, List<? extends FieldOperation> fieldOperations) {
        this.predicate = requireNonNull(predicate);
        this.fieldOperations = new ArrayList<>(requireNonNull(fieldOperations));
    }

    @Override
    public TypeMapper copy(String key, String target) {
        return newTypeMapper(new CopyFieldOperation(key, target));
    }

    @Override
    public TypeMapper copyRemaining() {
        return newTypeMapper(new CopyFieldOperation());
    }

    @Override
    public TypeMapper put(String key, String value) {
        return newTypeMapper(new PutFieldOperation(key, value));
    }

    private TypeMapper newTypeMapper(FieldOperation fieldOperation) {
        return new TypeMapperImpl(predicate, concat(fieldOperations, fieldOperation));
    }

    @Override
    public boolean test(ObjectTypeDefinition definition) {
        return predicate.test(definition);
    }

    @Override
    public SelectionSetMappingResult apply(MappingContext mappingContext, SelectionSet selectionSet) {
        final List<Selection> outputSelections = new ArrayList<>();

        final Map<Boolean, List<Selection>> fieldsAndNonFields =
                selectionSet.getSelections().stream().collect(groupingBy(s -> s instanceof Field));

        // take care of all non-Field selection
        outputSelections.addAll(fieldsAndNonFields.getOrDefault(false, emptyList()));

        // apply the type mapper to the selection fields
        final List<FieldOperationResult> fieldOperationResults =
                applyOperations(mappingContext, cast(fieldsAndNonFields.getOrDefault(true, emptyList())));

        final MapperOperation mapper = fieldOperationResults.stream()
                .peek(or -> or.getField().ifPresent(outputSelections::add))
                .map(FieldOperationResult::getMapper)
                .reduce((o1, o2) -> MapperOperations.composed(o1, o2))
                .orElse(MapperOperations.noop());


        return new SelectionSetMappingResult(new SelectionSet(outputSelections), mapper);
    }

    private List<FieldOperationResult> applyOperations(MappingContext mappingContext, List<Field> fields) {
        return fields.stream()
                .map(field -> applyOperation(mappingContext, field))
                .flatMap(OptionalHelper::toStream)
                .collect(toList());
    }

    private Optional<FieldOperationResult> applyOperation(MappingContext mappingContext, Field field) {
        return fieldOperations.stream()
                .filter(operation -> operation.test(field))
                .findFirst()
                .map(operation -> operation.apply(mappingContext, field));
    }
}
