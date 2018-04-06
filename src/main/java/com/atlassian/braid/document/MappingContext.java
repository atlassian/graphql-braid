package com.atlassian.braid.document;

import com.atlassian.braid.java.util.BraidObjects;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeInfo;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.findOperationDefinitions;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.document.Fields.maybeFindObjectTypeDefinition;
import static com.atlassian.braid.document.Fields.maybeGetTypeInfo;
import static com.atlassian.braid.document.TypeMappers.maybeFindTypeMapper;
import static com.atlassian.braid.java.util.BraidLists.concat;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

abstract class MappingContext {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;
    private final List<FragmentDefinition> fragmentDefinitions;

    MappingContext(MappingContext mappingContext) {
        this(mappingContext.schema, mappingContext.typeMappers, mappingContext.fragmentDefinitions);
    }

    MappingContext(TypeDefinitionRegistry schema,
                   List<TypeMapper> typeMappers,
                   List<FragmentDefinition> fragmentDefinitions) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
        this.fragmentDefinitions = requireNonNull(fragmentDefinitions);
    }

    final Optional<TypeMapper> getTypeMapper() {
        return maybeFindTypeMapper(typeMappers, getObjectTypeDefinition());
    }

    private Optional<FragmentDefinition> maybeGetFragmentDefinition(String name) {
        return fragmentDefinitions.stream().filter(fm -> fm.getName().equals(name)).findFirst();
    }

    final FragmentDefinition getFragmentDefinition(FragmentSpread fragmentSpread) {
        return maybeGetFragmentDefinition(fragmentSpread.getName()).orElseThrow(IllegalStateException::new);
    }

    protected List<String> getPath() {
        return Collections.emptyList();
    }

    final String getSpringPath(String targetKey) {
        return concat(getPath().stream(), Stream.of(targetKey)).map(p -> "['" + p + "']").collect(joining());
    }

    boolean inList() {
        return false;
    }

    protected abstract ObjectTypeDefinition getObjectTypeDefinition();

    MappingContext forField(Field field) {
        throw new IllegalStateException();
    }

    static RootMappingContext rootContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        return new RootMappingContext(schema, typeMappers, emptyList());
    }

    static final class RootMappingContext extends MappingContext {

        RootMappingContext(MappingContext parentContext, List<FragmentDefinition> fragmentMappings) {
            this(parentContext.schema, parentContext.typeMappers, fragmentMappings);
        }

        RootMappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings) {
            super(schema, typeMappers, fragmentMappings);
        }

        FragmentDefinitionMappingContext forFragment(FragmentDefinition definition) {
            return new FragmentDefinitionMappingContext(this, definition);
        }

        OperationDefinitionMappingContext forOperationDefinition(OperationDefinition definition) {
            return new OperationDefinitionMappingContext(this, definition);
        }

        RootMappingContext withFragments(List<FragmentDefinition> fragmentMappings) {
            return new RootMappingContext(this, fragmentMappings);
        }

        @Override
        protected ObjectTypeDefinition getObjectTypeDefinition() {
            throw new IllegalStateException();
        }
    }

    static final class FragmentDefinitionMappingContext extends MappingContext {

        private final ObjectTypeDefinition objectTypeDefinition;

        FragmentDefinitionMappingContext(MappingContext parentContext, FragmentDefinition definition) {
            super(parentContext);
            this.objectTypeDefinition = findFragmentObjectTypeDefinition(parentContext.schema, definition);
        }

        @Override
        public ObjectTypeDefinition getObjectTypeDefinition() {
            return objectTypeDefinition;
        }

        private static ObjectTypeDefinition findFragmentObjectTypeDefinition(TypeDefinitionRegistry schema, FragmentDefinition definition) {
            return schema.getType(definition.getTypeCondition().getName()).map(ObjectTypeDefinition.class::cast).orElseThrow(IllegalStateException::new);
        }
    }

    static abstract class NodeMappingContext extends MappingContext {

        NodeMappingContext(MappingContext parentContext) {
            super(parentContext);
        }

        @Override
        NodeMappingContext forField(Field field) {
            return new FieldMappingContext(this, field);
        }
    }

    static class OperationDefinitionMappingContext extends NodeMappingContext {
        private final ObjectTypeDefinition objectTypeDefinition;

        OperationDefinitionMappingContext(MappingContext parentContext, OperationDefinition operationDefinition) {
            super(parentContext);
            this.objectTypeDefinition = findOperationTypeDefinition(parentContext.schema, operationDefinition);
        }

        @Override
        public ObjectTypeDefinition getObjectTypeDefinition() {
            return objectTypeDefinition;
        }

        private static ObjectTypeDefinition findOperationTypeDefinition(TypeDefinitionRegistry schema, OperationDefinition op) {
            return findOperationDefinitions(schema)
                    .flatMap(maybeFindOperationTypeDefinition(op))
                    .map(OperationTypeDefinition::getType)
                    .flatMap(schema::getType)
                    .map(BraidObjects::<ObjectTypeDefinition>cast)
                    .orElseThrow(IllegalStateException::new);
        }

        private static Function<List<OperationTypeDefinition>, Optional<OperationTypeDefinition>> maybeFindOperationTypeDefinition(OperationDefinition op) {
            return ops -> ops.stream().filter(isOperationTypeDefinitionForOperationType(op)).findFirst();
        }

        private static Predicate<OperationTypeDefinition> isOperationTypeDefinitionForOperationType(OperationDefinition op) {
            return otd -> otd.getName().equalsIgnoreCase(op.getOperation().name());
        }
    }

    private static class FieldMappingContext extends NodeMappingContext {

        private final Field field;
        private final List<String> parentPath;
        private final TypeInfo typeInfo;
        private final ObjectTypeDefinition objectTypeDefinition;

        FieldMappingContext(MappingContext parentContext, Field field) {
            super(parentContext);
            this.field = requireNonNull(field);
            this.parentPath = parentContext.getPath();
            this.typeInfo = maybeGetTypeInfo(parentContext.getObjectTypeDefinition(), field).orElseThrow(IllegalStateException::new);
            this.objectTypeDefinition = maybeFindObjectTypeDefinition(parentContext.schema, this.typeInfo).orElseThrow(IllegalStateException::new);
        }

        @Override
        protected ObjectTypeDefinition getObjectTypeDefinition() {
            return objectTypeDefinition;
        }

        @Override
        protected List<String> getPath() {
            return inList() ? emptyList() : concat(parentPath, getFieldAliasOrName(field));
        }

        @Override
        boolean inList() {
            return typeInfo.isList();
        }
    }
}
