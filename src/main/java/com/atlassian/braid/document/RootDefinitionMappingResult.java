package com.atlassian.braid.document;

import com.atlassian.braid.document.SelectionOperation.OperationResult;
import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Definition;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
 * {@link OperationResult}s into an {@link RootDefinitionMappingResult}
 */
abstract class RootDefinitionMappingResult<D extends Definition> {
    protected final D definition;
    protected final List<Selection> selections;
    protected final List<MapperOperation> mappers;

    private RootDefinitionMappingResult(D definition, List<Selection> selections) {
        this(definition, new ArrayList<>(selections), new ArrayList<>());
    }

    private RootDefinitionMappingResult(D definition,
                                        List<Selection> selections,
                                        List<MapperOperation> mappers) {
        this.definition = requireNonNull(definition);
        this.selections = requireNonNull(selections);
        this.mappers = requireNonNull(mappers);
    }

    /**
     * Transform the result into a <em>new</em> {@link OperationDefinition} based on the given
     * {@link #definition operation definition} and the mapped {@link Selection selections}
     *
     * @return a mapped {@link OperationDefinition}
     */
    abstract D toDefinition();

    List<MapperOperation> getMapperOperations() {
        return unmodifiableList(mappers);
    }

    private void add(OperationResult result) {
        result.getSelection().ifPresent(selections::add);
        mappers.add(result.getMapper());
    }

    private static <D extends Definition> RootDefinitionMappingResult<D> combine(Function<D, RootDefinitionMappingResult<D>> constructor, RootDefinitionMappingResult<D> omr1, RootDefinitionMappingResult<D> omr2) {
        if (!Objects.equals(omr1.definition, omr2.definition)) {
            throw new IllegalArgumentException();
        }

        final RootDefinitionMappingResult<D> result = constructor.apply(omr1.definition);
        result.selections.addAll(omr1.selections);
        result.selections.addAll(omr2.selections);

        result.mappers.addAll(omr1.mappers);
        result.mappers.addAll(omr2.mappers);

        return result;
    }

    /**
     * Returns a collector used to collect {@link OperationResult}s into an {@link RootDefinitionMappingResult}
     *
     * @param operation  the operation definition being collected
     * @param selections the initial selections to add, i.e. if only fields are being mapped, other selections can
     *                   be added directly here
     * @return a {@link Collector} of {@link OperationResult} to {@link RootDefinitionMappingResult}
     */
    static Collector<OperationResult, RootDefinitionMappingResult<OperationDefinition>, RootDefinitionMappingResult<OperationDefinition>> toOperationMappingResult(
            OperationDefinition operation, List<Selection> selections) {
        return Collector.of(
                () -> new OperationMappingResult(operation, selections),
                RootDefinitionMappingResult::add,
                (omr1, omr2) -> combine(OperationMappingResult::new, omr1, omr2));
    }

    static Collector<OperationResult, RootDefinitionMappingResult<FragmentDefinition>, RootDefinitionMappingResult<FragmentDefinition>> toFragmentMappingResult(
            FragmentDefinition fragment, List<Selection> selections) {
        return Collector.of(
                () -> new FragmentMappingResult(fragment, selections),
                RootDefinitionMappingResult::add,
                (omr1, omr2) -> combine(FragmentMappingResult::new, omr1, omr2));
    }

    private static class OperationMappingResult extends RootDefinitionMappingResult<OperationDefinition> {

        private OperationMappingResult(OperationDefinition definition) {
            this(definition, emptyList());
        }

        private OperationMappingResult(OperationDefinition definition, List<Selection> selections) {
            super(definition, selections);
        }

        @Override
        OperationDefinition toDefinition() {
            return new OperationDefinition(
                    definition.getName(),
                    definition.getOperation(),
                    definition.getVariableDefinitions(),
                    definition.getDirectives(),
                    new SelectionSet(selections));
        }
    }

    private static class FragmentMappingResult extends RootDefinitionMappingResult<FragmentDefinition> {

        private FragmentMappingResult(FragmentDefinition definition) {
            this(definition, emptyList());
        }

        private FragmentMappingResult(FragmentDefinition definition, List<Selection> selections) {
            super(definition, selections);
        }


        @Override
        FragmentDefinition toDefinition() {
            return new FragmentDefinition(
                    definition.getName(),
                    definition.getTypeCondition(),
                    definition.getDirectives(),
                    new SelectionSet(selections));
        }
    }
}
