package com.atlassian.braid.document;

import com.atlassian.braid.document.SelectionOperation.OperationResult;
import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import com.atlassian.braid.source.OptionalHelper;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.java.util.BraidLists.concat;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * <strong>Internal</strong> implementation of the {@link TypeMapper}
 *
 * @see TypeMapper
 */
final class TypeMapperImpl implements TypeMapper {

    private final static SelectionOperation FRAGMENT_OPERATION = new FragmentSpreadOperation();
    private final static SelectionOperation INLINE_FRAGMENT_OPERATION = new InlineFragmentOperation();

    private final Predicate<ObjectTypeDefinition> predicate;
    private final List<SelectionOperation> selectionOperations;

    TypeMapperImpl(Predicate<ObjectTypeDefinition> predicate) {
        this(predicate, emptyList());
    }

    TypeMapperImpl(Predicate<ObjectTypeDefinition> predicate, List<? extends SelectionOperation> selectionOperations) {
        this.predicate = requireNonNull(predicate);
        this.selectionOperations = new ArrayList<>(requireNonNull(selectionOperations));
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

    private TypeMapper newTypeMapper(SelectionOperation fieldOperation) {
        return new TypeMapperImpl(predicate, concat(selectionOperations, fieldOperation));
    }

    @Override
    public boolean test(ObjectTypeDefinition definition) {
        return predicate.test(definition);
    }

    @Override
    public SelectionSetMappingResult apply(MappingContext mappingContext, SelectionSet selectionSet) {
        final List<Selection> outputSelections = new ArrayList<>();

        // TODO nicer reduce...
        final MapperOperation mapper = applyOperations(mappingContext, selectionSet.getSelections())
                .stream()
                .peek(or -> or.getSelection().ifPresent(outputSelections::add))
                .map(OperationResult::getMapper)
                .reduce((o1, o2) -> MapperOperations.composed(o1, o2))
                .orElse(MapperOperations.noop());

        return new SelectionSetMappingResult(new SelectionSet(outputSelections), mapper);
    }

    private List<OperationResult> applyOperations(MappingContext mappingContext, List<Selection> selections) {
        return selections.stream()
                .map(selection -> applyOperation(mappingContext, selection))
                .flatMap(OptionalHelper::toStream)
                .collect(toList());
    }

    private Optional<OperationResult> applyOperation(MappingContext mappingContext, Selection selection) {
        return newSelectionOperationsStream()
                .filter(operation -> operation.test(selection))
                .findFirst()
                .map(operation -> operation.apply(mappingContext, selection));
    }

    private Stream<SelectionOperation> newSelectionOperationsStream() {
        return Stream.concat(selectionOperations.stream(), Stream.of(FRAGMENT_OPERATION, INLINE_FRAGMENT_OPERATION));
    }
}
