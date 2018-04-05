package com.atlassian.braid.document;

import com.atlassian.braid.java.util.BraidObjects;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.TypeUtils.findOperationDefinitions;
import static java.util.Collections.emptyList;

final class MappingContexts {

    static RootMappingContext rootContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        return new RootMappingContext(schema, typeMappers, emptyList());
    }

    static class OperationDefinitionMappingContext extends MappingContext {
        OperationDefinitionMappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings, ObjectTypeDefinition operationTypeDefinition) {
            super(schema, typeMappers, fragmentMappings, () -> operationTypeDefinition);

        }
    }

    static final class RootMappingContext extends MappingContext {

        RootMappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings) {
            super(schema, typeMappers, fragmentMappings);
        }

        FragmentDefinitionMappingContext forFragment(FragmentDefinition definition) {
            return new FragmentDefinitionMappingContext(this.schema, this.typeMappers, this.fragmentMappings, getFragmentObjectTypeDefinition(definition));
        }

        OperationDefinitionMappingContext forOperationDefinition(OperationDefinition definition) {
            return new OperationDefinitionMappingContext(this.schema, this.typeMappers, this.fragmentMappings, findOperationTypeDefinition(schema, definition));
        }

        private ObjectTypeDefinition getFragmentObjectTypeDefinition(FragmentDefinition definition) {
            return schema.getType(definition.getTypeCondition().getName()).map(ObjectTypeDefinition.class::cast).orElseThrow(IllegalStateException::new);
        }

        RootMappingContext withFragments(List<FragmentDefinition> fragmentMappings) {
            return new RootMappingContext(this.schema, this.typeMappers, fragmentMappings);
        }
    }

    static class FragmentDefinitionMappingContext extends MappingContext {

        private final ObjectTypeDefinition objectTypeDefinition;

        FragmentDefinitionMappingContext(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings, ObjectTypeDefinition objectTypeDefinition) {
            super(schema, typeMappers, fragmentMappings);
            this.objectTypeDefinition = objectTypeDefinition;
        }

        @Override
        public boolean inList() {
            return false;
        }

        @Override
        public ObjectTypeDefinition getObjectTypeDefinition() {
            return objectTypeDefinition;
        }

        @Override
        public Field getField() {
            return null;
        }

        @Override
        public String getSpringPath(String targetKey) {
            return "['" + targetKey + "']";
        }

        @Override
        public MappingContext toField(Field field) {
            return this;
        }
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
