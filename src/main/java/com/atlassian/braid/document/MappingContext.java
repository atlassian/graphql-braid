package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.atlassian.braid.document.Fields.findObjectTypeDefinition;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

final class MappingContext {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;
    private final List<String> path;
    private final ObjectTypeDefinition objectTypeDefinition;
    private final Field field;

    private MappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<String> path, ObjectTypeDefinition objectTypeDefinition, Field field) {
        this.schema = schema;
        this.typeMappers = typeMappers;
        this.path = path;
        this.objectTypeDefinition = objectTypeDefinition;
        this.field = field;
    }

    static MappingContext of(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, ObjectTypeDefinition definition, Field field) {
        return new MappingContext(schema, typeMappers, singletonList(getFieldAliasOrName(field)), definition, field);
    }

    MappingContext to(Field field) {
        return new MappingContext(
                this.schema,
                this.typeMappers,
                addFieldToPath(this.path, field),
                findObjectTypeDefinition(this.schema, this.objectTypeDefinition, field),
                field);
    }

    String getSpringPath(String targetKey) {
        return Stream.concat(path.stream(), Stream.of(targetKey))
                .map(p -> "['" + p + "']")
                .collect(joining());
    }

    List<TypeMapper> getTypeMappers() {
        return typeMappers;
    }

    public TypeDefinitionRegistry getSchema() {
        return schema;
    }

    Field getField() {
        return field;
    }

    ObjectTypeDefinition getObjectTypeDefinition() {
        return objectTypeDefinition;
    }

    private static List<String> addFieldToPath(List<String> path, Field field) {
        final List<String> paths = new ArrayList<>();
        paths.addAll(path);
        paths.add(getFieldAliasOrName(field));
        return paths;
    }
}
