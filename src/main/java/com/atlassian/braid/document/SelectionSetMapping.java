package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import graphql.language.SelectionSet;

import java.util.Map;
import java.util.function.Function;

final class SelectionSetMapping {
    private final SelectionSet selectionSet;
    private final MapperOperation resultMapper;

    SelectionSetMapping(SelectionSet selectionSet) {
        this(selectionSet, MapperOperations.noop());
    }

    SelectionSetMapping(SelectionSet selectionSet, MapperOperation resultMapper) {
        this.selectionSet = selectionSet;
        this.resultMapper = resultMapper;
    }

    SelectionSet getSelectionSet() {
        return selectionSet;
    }

    MapperOperation getResultMapper() {
        return resultMapper;
    }
}
