package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

public class MappingContext {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;
    private final List<String> path;
    private final ObjectTypeDefinition objectTypeDefinition;
    private final Field field;

    public MappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<String> path, ObjectTypeDefinition objectTypeDefinition, Field field) {
        this.schema = schema;
        this.typeMappers = typeMappers;
        this.path = path;
        this.objectTypeDefinition = objectTypeDefinition;
        this.field = field;
    }

    public Field getField() {
        return field;
    }

    public static MappingContext of(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, ObjectTypeDefinition definition, Field field, String... path) {
        return new MappingContext(schema, typeMappers, asList(path), definition, field);
    }

    public static MappingContext from(MappingContext mappingContext, ObjectTypeDefinition objectTypeDefinition, Field field) {
        final ArrayList<String> paths = new ArrayList<>();
        paths.addAll(mappingContext.path);
        paths.add(field.getAlias() != null ? field.getAlias() : field.getName());// TODO do something intelligent here
        return new MappingContext(mappingContext.schema, mappingContext.typeMappers, paths, objectTypeDefinition, field);
    }

    public String getSpringPath(String targetKey) {
        return Stream.concat(path.stream(), Stream.of(targetKey))
                .map(p -> "['" + p + "']")
                .collect(joining());
    }

    public List<TypeMapper> getTypeMappers() {
        return typeMappers;
    }

    public TypeDefinitionRegistry getSchema() {
        return schema;
    }

    public ObjectTypeDefinition getObjectTypeDefinition() {
        return objectTypeDefinition;
    }
}
