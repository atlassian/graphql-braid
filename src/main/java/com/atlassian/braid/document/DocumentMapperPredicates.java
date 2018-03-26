package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;

import java.util.Objects;
import java.util.function.Predicate;

@SuppressWarnings("WeakerAccess")
public final class DocumentMapperPredicates {
    private DocumentMapperPredicates() {
    }

    public static Predicate<ObjectTypeDefinition> typeNamed(String name) {
        return type -> Objects.equals(type.getName(), name);
    }

    public static Predicate<Field> fieldNamed(String name) {
        return field -> Objects.equals(field.getName(), name);
    }

    public static <T> Predicate<T> all() {
        return __ -> true;
    }
}
