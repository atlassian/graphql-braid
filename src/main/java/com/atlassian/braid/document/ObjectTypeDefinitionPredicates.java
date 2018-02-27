package com.atlassian.braid.document;

import graphql.language.ObjectTypeDefinition;

import java.util.Objects;
import java.util.function.Predicate;

public final class ObjectTypeDefinitionPredicates {
    private ObjectTypeDefinitionPredicates() {
    }

    public static Predicate<ObjectTypeDefinition> typeNamed(String name) {
        return type -> Objects.equals(type.getName(), name);
    }
}
