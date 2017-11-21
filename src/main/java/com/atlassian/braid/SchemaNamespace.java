package com.atlassian.braid;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Defines a (strongly typed) namespace for a {@link SchemaSource}
 */
public final class SchemaNamespace {

    private final String value;

    private SchemaNamespace(String value) {
        this.value = requireNonNull(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaNamespace that = (SchemaNamespace) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "SchemaNamespace(" + value + ")";
    }

    public static SchemaNamespace of(String value) {
        return new SchemaNamespace(value);
    }
}
