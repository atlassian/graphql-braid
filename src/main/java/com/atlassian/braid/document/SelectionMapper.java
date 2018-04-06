package com.atlassian.braid.document;

import com.atlassian.braid.document.SelectionOperation.OperationResult;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;

import static com.atlassian.braid.document.SelectionOperation.result;
import static java.util.Objects.requireNonNull;

abstract class SelectionMapper {

    static SelectionMapper getSelectionMapper(Selection selection) {
        if (selection instanceof Field) {
            return new FieldMapper((Field) selection);
        } else if (selection instanceof FragmentSpread) {
            return new FragmentSpreadMapper((FragmentSpread) selection);
        } else if (selection instanceof InlineFragment) {
            throw new IllegalStateException("Inline fragments are not supported with document mapping! (yet)");
        } else {
            throw new IllegalStateException();
        }
    }

    abstract OperationResult map(MappingContext mappingContext);

    private static class FieldMapper extends SelectionMapper {
        private final Field field;

        private FieldMapper(Field field) {
            this.field = requireNonNull(field);
        }

        OperationResult map(MappingContext mappingContext) {
            final MappingContext fieldMappingContext = mappingContext.forField(field);

            return fieldMappingContext.getTypeMapper()
                    .map(typeMapper -> typeMapper.apply(fieldMappingContext, field.getSelectionSet()))
                    .map(mappingResult -> mappingResult.toOperationResult(field, fieldMappingContext))
                    .orElseGet(() -> result(field));
        }
    }

    private static class FragmentSpreadMapper extends SelectionMapper {
        private final FragmentSpread fragmentSpread;

        private FragmentSpreadMapper(FragmentSpread fragmentSpread) {
            this.fragmentSpread = requireNonNull(fragmentSpread);
        }

        @Override
        OperationResult map(MappingContext mappingContext) {
            final FragmentDefinition fragmentDefinition = mappingContext.getFragmentDefinition(fragmentSpread);
            return mappingContext.getTypeMapper()
                    .map(tm -> tm.apply(mappingContext, fragmentDefinition.getSelectionSet()))
                    .map(mappingResult -> mappingResult.toOperationResult(fragmentSpread))
                    .orElseGet(() -> result(fragmentSpread));
        }
    }
}
