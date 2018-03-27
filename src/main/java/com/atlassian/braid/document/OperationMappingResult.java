package com.atlassian.braid.document;

import com.atlassian.braid.document.FieldOperation.FieldOperationResult;
import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * <p>This is an intermediary <strong>internal</strong> <em>mutable</em> class used to collect single GraphQL operation
 * mapping results, i.e. the mapped {@link Selection selections} for a given {@link OperationDefinition}
 * <p>It collects both the operation selections and the {@link MapperOperation mapper operations} used to process
 * the queried data
 * <p>The main entry point of this class is its {@link #toOperationMappingResult(OperationDefinition, List) collector} that allows collecting
 * {@link FieldOperationResult}s into an {@link OperationMappingResult}
 */
final class OperationMappingResult {
    private final OperationDefinition operationDefinition;
    private final List<Selection> selections;
    private final List<MapperOperation> mappers;

    private OperationMappingResult(OperationDefinition operationDefinition) {
        this(operationDefinition, emptyList());
    }

    private OperationMappingResult(OperationDefinition operationDefinition, List<Selection> selections) {
        this(operationDefinition, new ArrayList<>(selections), new ArrayList<>());
    }

    private OperationMappingResult(OperationDefinition operationDefinition,
                                   List<Selection> selections,
                                   List<MapperOperation> mappers) {
        this.operationDefinition = requireNonNull(operationDefinition);
        this.selections = requireNonNull(selections);
        this.mappers = requireNonNull(mappers);
    }

    /**
     * Transform the result into a <em>new</em> {@link OperationDefinition} based on the given
     * {@link #operationDefinition operation definition} and the mapped {@link Selection selections}
     *
     * @return a mapped {@link OperationDefinition}
     */
    OperationDefinition toOperationDefinition() {
        return new OperationDefinition(
                operationDefinition.getName(),
                operationDefinition.getOperation(),
                operationDefinition.getVariableDefinitions(),
                operationDefinition.getDirectives(),
                new SelectionSet(selections));
    }

    List<MapperOperation> getMapperOperations() {
        return unmodifiableList(mappers);
    }

    private void add(FieldOperationResult result) {
        result.getField().ifPresent(selections::add);
        mappers.add(result.getMapper());
    }

    private static OperationMappingResult combine(OperationMappingResult omr1, OperationMappingResult omr2) {
        if (!Objects.equals(omr1.operationDefinition, omr2.operationDefinition)) {
            throw new IllegalArgumentException();
        }

        final OperationMappingResult result = new OperationMappingResult(omr1.operationDefinition);
        result.selections.addAll(omr1.selections);
        result.selections.addAll(omr2.selections);

        result.mappers.addAll(omr1.mappers);
        result.mappers.addAll(omr2.mappers);

        return result;
    }

    /**
     * Returns a collector used to collect {@link FieldOperationResult}s into an {@link OperationMappingResult}
     *
     * @param operation  the operation definition being collected
     * @param selections the initial selections to add, i.e. if only fields are being mapped, other selections can
     *                   be added directly here
     * @return a {@link Collector} of {@link FieldOperationResult} to {@link OperationMappingResult}
     */
    static Collector<FieldOperationResult, OperationMappingResult, OperationMappingResult> toOperationMappingResult(
            OperationDefinition operation, List<Selection> selections) {
        return Collector.of(
                () -> new OperationMappingResult(operation, selections),
                OperationMappingResult::add,
                OperationMappingResult::combine);
    }
}
