package com.atlassian.braid.document;

import com.atlassian.braid.document.FieldOperation.FieldOperationResult;
import com.atlassian.braid.mapper.Mapper;
import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.function.BiFunction;

import static com.atlassian.braid.document.FieldOperation.result;
import static com.atlassian.braid.document.Fields.cloneFieldWithNewSelectionSet;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.mapper.Mappers.mapper;
import static java.util.Objects.requireNonNull;

/**
 * <p>This is an intermediary <strong>internal</strong> <em>mutable</em> class for {@link SelectionSet selection set}
 * mapping results
 */
final class SelectionSetMappingResult {
    private final SelectionSet selectionSet;
    private final MapperOperation resultMapper;

    SelectionSetMappingResult(SelectionSet selectionSet, MapperOperation resultMapper) {
        this.selectionSet = requireNonNull(selectionSet);
        this.resultMapper = requireNonNull(resultMapper);
    }

    FieldOperationResult toFieldOperationResult(MappingContext mappingContext) {
        final Field field = mappingContext.getField();
        return result(
                cloneFieldWithNewSelectionSet(field, selectionSet),
                getMapperOperation(mappingContext).apply(getFieldAliasOrName(field), mapper(resultMapper)));
    }

    private static BiFunction<String, Mapper, MapperOperation> getMapperOperation(MappingContext mappingContext) {
        return mappingContext.getTypeInfo().isList() ? MapperOperations::copyList : MapperOperations::map;
    }
}
