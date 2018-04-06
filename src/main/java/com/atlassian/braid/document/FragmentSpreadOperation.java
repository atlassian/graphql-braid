package com.atlassian.braid.document;

import graphql.language.FragmentSpread;

import static com.atlassian.braid.document.SelectionMapper.getSelectionMapper;

final class FragmentSpreadOperation extends AbstractTypeOperation<FragmentSpread> {

    FragmentSpreadOperation() {
        super(FragmentSpread.class);
    }

    @Override
    protected OperationResult applyToType(MappingContext mappingContext, FragmentSpread fragmentSpread) {
        return getSelectionMapper(fragmentSpread).map(mappingContext);
    }
}
