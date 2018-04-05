package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

abstract class MappingContext {

    protected final TypeDefinitionRegistry schema;
    protected final List<TypeMapper> typeMappers;
    protected final List<FragmentDefinition> fragmentMappings;

    protected final Supplier<ObjectTypeDefinition> objectTypeDefinition;

    List<String> path;

    MappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings) {
        this(schema, typeMappers, fragmentMappings, () -> null);
    }

    MappingContext(TypeDefinitionRegistry schema,
                   List<TypeMapper> typeMappers,
                   List<FragmentDefinition> fragmentMappings,
                   Supplier<ObjectTypeDefinition> objectTypeDefinition) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
        this.fragmentMappings = requireNonNull(fragmentMappings);
        this.objectTypeDefinition = requireNonNull(objectTypeDefinition);
    }


    final List<TypeMapper> getTypeMappers() {
        return Collections.unmodifiableList(typeMappers);
    }

    final Optional<FragmentDefinition> getFragmentMapping(String name) {
        return fragmentMappings.stream().filter(fm -> fm.getName().equals(name)).findFirst();
    }

    String getSpringPath(String targetKey) {
        return "['" + targetKey + "']";
    }

    boolean inList() {
        return false;
    }

    ObjectTypeDefinition getObjectTypeDefinition() {
        return objectTypeDefinition.get();
    }

    Field getField() {
        return null;
    }

    MappingContext toField(Field field) {
//        return new FieldMappingContext(
//                this.schema,
//                this.typeMappers,
//                this.getPath(),
//                this.getObjectTypeDefinition(),
//                field);
        return null;
    }


    static MappingContext forField(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings, ObjectTypeDefinition definition, Field field) {
        return new FieldMappingContext(schema, typeMappers, fragmentMappings, emptyList(), definition, field);
    }

    public static class FragmentMapping {

        final FragmentDefinition fragmentDefinition;
        public final MapperOperation resultMapper;

        public FragmentMapping(FragmentDefinition fragmentDefinition, MapperOperation resultMapper) {

            this.fragmentDefinition = fragmentDefinition;
            this.resultMapper = resultMapper;
        }


    }
}
