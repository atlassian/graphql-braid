package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Simple utility class to deal with common field operations
 */
final class Fields {

    private Fields() {
    }

    static ObjectTypeDefinition findObjectTypeDefinition(
            TypeDefinitionRegistry schema, ObjectTypeDefinition parent, Field field) {
        return parent.getFieldDefinitions().stream()
                .filter(fd -> fd.getName().equals(field.getName())).findFirst()
                .flatMap(fd -> schema.getType(fd.getType()))
                .map(ObjectTypeDefinition.class::cast)
                .orElseThrow(IllegalStateException::new);
    }

    static String getFieldAliasOrName(Field field) {
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }

    static Field cloneFieldWithNewSelectionSet(Field field, SelectionSet selectionSet) {
        return new Field(field.getName(), field.getAlias(), field.getArguments(), field.getDirectives(), selectionSet);
    }

    static Field cloneFieldWithNewName(Field field, String newName) {
        return new Field(newName, field.getAlias(), field.getArguments(), field.getDirectives(), field.getSelectionSet());
    }
}
