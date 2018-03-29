package com.atlassian.braid.document;

import graphql.language.Field;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

abstract class AbstractFieldOperation implements FieldOperation {

    private final Predicate<Field> predicate;

    AbstractFieldOperation(Predicate<Field> predicate) {
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public final boolean test(Field field) {
        return predicate.test(field);
    }
}
