package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;

import static java.util.Collections.emptyList;

interface MappingContext {

    List<TypeMapper> getTypeMappers();

    boolean inList();

    ObjectTypeDefinition getObjectTypeDefinition();

    Field getField();

    String getSpringPath(String targetKey);

    MappingContext toField(Field field);

    static MappingContext of(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, ObjectTypeDefinition definition, Field field) {
        return new MappingContextImpl(schema, typeMappers, emptyList(), definition, field);
    }
}
