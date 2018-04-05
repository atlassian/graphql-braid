package com.atlassian.braid.document;

import com.atlassian.braid.document.SelectionOperation.OperationResult;
import com.atlassian.braid.mapper.Mapper;
import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.function.BiFunction;

import static com.atlassian.braid.document.SelectionOperation.result;
import static com.atlassian.braid.document.Fields.cloneFieldWithNewSelectionSet;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.mapper.Mappers.mapper;
import static java.util.Objects.requireNonNull;

/**
 * <p>This is an intermediary <strong>internal</strong> <em>mutable</em> class for {@link SelectionSet selection set}
 * mapping results
 */
final class SelectionSetMappingResult {
    final SelectionSet selectionSet;
     final MapperOperation resultMapper;

    SelectionSetMappingResult(SelectionSet selectionSet, MapperOperation resultMapper) {
        this.selectionSet = requireNonNull(selectionSet);
        this.resultMapper = requireNonNull(resultMapper);
    }

    OperationResult toFieldOperationResult(MappingContext mappingContext) {
        final Field field = mappingContext.getField();
        return result(
                cloneFieldWithNewSelectionSet(field, selectionSet),
                getMapperOperation(mappingContext).apply(getFieldAliasOrName(field), mapper(resultMapper)));
    }

    private static BiFunction<String, Mapper, MapperOperation> getMapperOperation(MappingContext mappingContext) {
        return mappingContext.inList() ? MapperOperations::copyList : MapperOperations::map;
    }
}
