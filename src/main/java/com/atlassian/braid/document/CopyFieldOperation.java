package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import com.atlassian.braid.mapper.Mappers;
import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;

import static com.atlassian.braid.document.TypedDocumentMapper.findOutputTypeForField;
import static java.util.Objects.requireNonNull;

public final class CopyFieldOperation implements FieldOperation {

    private final String name;
    private final String target;

    public CopyFieldOperation(String name, String target) {
        this.name = requireNonNull(name);
        this.target = requireNonNull(target);
    }

    @Override
    public boolean test(Field field) {
        return field.getName().equals(name);
    }

    @Override
    public OperationResult apply(MappingContext mappingContext, Field field) {
//        if(!field.getSelectionSet().getSelections().isEmpty()) {
//            throw new IllegalStateException("This is supposed to be a scalar field");
//        }

        final SelectionSet selectionSet = field.getSelectionSet();
        if (selectionSet != null) {
            // we need todo something here...
            return mapSelectionSet(mappingContext, field);
        } else {

            final Field newField = new Field(target, field.getAlias(), field.getArguments(), field.getDirectives(), null);
            final String alias = newField.getAlias();

            final String sourceKey = alias == null ? name : alias;
            final String targetKey = alias == null ? target : alias;

            final MapperOperation operation = MapperOperations.copy(mappingContext.getSpringPath(targetKey), sourceKey);
            return FieldOperation.result(newField, operation);
        }
    }

    private OperationResult mapSelectionSet(MappingContext mappingContext, Field field) {
        final ObjectTypeDefinition definition = findOutputTypeForField(mappingContext.getSchema(), mappingContext.getObjectTypeDefinition(), field);

        final MappingContext newMappingContext = MappingContext.from(mappingContext, definition, field);
        return mappingContext.getTypeMappers()
                .stream()
                .filter(tm -> tm.test(definition))
                .findFirst()
                .map(tm -> {
                    final SelectionSetMapping apply = tm.apply(newMappingContext, field.getSelectionSet());
                    final Field newField = new Field(field.getName(), field.getAlias(), field.getArguments(), field.getDirectives(), apply.getSelectionSet());

                    final MapperOperation mapper = MapperOperations.map(
                            field.getAlias() != null ? field.getAlias() : field.getName(),
                            Mappers.mapper(apply.getResultMapper()));

                    return FieldOperation.result(newField, mapper);
                })
                .orElse(FieldOperation.result(field));
    }
}
