package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeInfo;

import java.util.Optional;

/**
 * Simple utility class to deal with common field operations
 */
final class Fields {

    private Fields() {
    }

    static ObjectTypeDefinition findObjectTypeDefinition(
            TypeDefinitionRegistry schema, ObjectTypeDefinition parent, Field field) {
        return maybeFindObjectTypeDefinition(schema, maybeGetTypeInfo(parent, field))
                .orElseThrow(IllegalStateException::new);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static Optional<ObjectTypeDefinition> maybeFindObjectTypeDefinition(TypeDefinitionRegistry schema, Optional<TypeInfo> typeInfo) {
        return typeInfo
                .map(TypeInfo::getName)
                .flatMap(schema::getType)
                .map(ObjectTypeDefinition.class::cast);
    }

    static Optional<TypeInfo> maybeGetTypeInfo(ObjectTypeDefinition parent, Field field) {
        return parent.getFieldDefinitions().stream()
                .filter(fd -> fd.getName().equals(field.getName()))
                .findFirst()
                .map(FieldDefinition::getType)
                .map(TypeInfo::typeInfo);
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
