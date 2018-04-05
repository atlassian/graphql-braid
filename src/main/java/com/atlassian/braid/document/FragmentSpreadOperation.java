package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;

import java.util.Optional;

final class FragmentSpreadOperation extends AbstractTypeOperation<FragmentSpread> {

    FragmentSpreadOperation() {
        super(FragmentSpread.class);
    }

    @Override
    protected OperationResult applyToType(MappingContext mappingContext, FragmentSpread selection) {
        final Optional<FragmentDefinition> fragmentMapping = mappingContext.getFragmentMapping(selection.getName());

        final Optional<TypeMapper> first = mappingContext.getTypeMapper();

        final MapperOperation operation = first.map(tm -> tm.apply(mappingContext, fragmentMapping.get().getSelectionSet())).map(m -> m.resultMapper).orElse(null);

//        final MapperOperation operation = fragmentMapping.map(fm -> fm.resultMapper).orElse(null);

        return SelectionOperation.result(selection, operation);
    }
}
