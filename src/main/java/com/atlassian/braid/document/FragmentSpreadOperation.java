package com.atlassian.braid.document;

import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;

import static com.atlassian.braid.document.SelectionOperation.result;

final class FragmentSpreadOperation extends AbstractTypeOperation<FragmentSpread> {

    FragmentSpreadOperation() {
        super(FragmentSpread.class);
    }

    @Override
    protected OperationResult applyToType(MappingContext mappingContext, FragmentSpread fragmentSpread) {
        final FragmentDefinition fragmentDefinition = mappingContext.getFragmentDefinition(fragmentSpread);
        return mappingContext.getTypeMapper()
                .map(tm -> tm.apply(mappingContext, fragmentDefinition.getSelectionSet()))
                .map(mappingResult -> mappingResult.toOperationResult(fragmentSpread))
                .orElseGet(() -> result(fragmentSpread));
    }
}
