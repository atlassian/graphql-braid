package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeInfo;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.document.Fields.maybeFindObjectTypeDefinition;
import static com.atlassian.braid.document.Fields.maybeGetTypeInfo;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

final class MappingContext {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;
    private final Field field;

    private final Supplier<Optional<TypeInfo>> typeInfo;
    private final Supplier<Optional<ObjectTypeDefinition>> objectTypeDefinition;
    private final Supplier<List<String>> path;

    private MappingContext(TypeDefinitionRegistry schema,
                           List<TypeMapper> typeMappers,
                           List<String> parentPath,
                           ObjectTypeDefinition parentObjectTypeDefinition,
                           Field field) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
        this.field = requireNonNull(field);

        this.typeInfo = memoize(() -> maybeGetTypeInfo(parentObjectTypeDefinition, field));
        this.objectTypeDefinition = memoize(() -> maybeFindObjectTypeDefinition(schema, typeInfo.get()));
        this.path = memoize(() -> getTypeInfo().isList() ? emptyList() : appendToList(parentPath, getFieldAliasOrName(field)));
    }

    static MappingContext of(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, ObjectTypeDefinition definition, Field field) {
        return new MappingContext(schema, typeMappers, emptyList(), definition, field);
    }

    MappingContext to(Field field) {
        return new MappingContext(
                this.schema,
                this.typeMappers,
                this.getPath(),
                this.getObjectTypeDefinition(),
                field);
    }

    private List<String> getPath() {
        return path.get();
    }

    String getSpringPath(String targetKey) {
        return appendAsStream(getPath(), targetKey).map(p -> "['" + p + "']").collect(joining());
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

    TypeInfo getTypeInfo() {
        return typeInfo.get().orElseThrow(IllegalStateException::new);
    }

    ObjectTypeDefinition getObjectTypeDefinition() {
        return objectTypeDefinition.get().orElseThrow(IllegalStateException::new);
    }

    private static <T> Supplier<T> memoize(Supplier<T> supplier) {
        return new MemoizingSupplier<>(supplier);
    }

    private static <T> List<T> appendToList(List<T> list, T element) {
        return appendAsStream(list, element).collect(toList());
    }

    private static <T> Stream<T> appendAsStream(List<T> list, T element) {
        return Stream.concat(list.stream(), Stream.of(element));
    }


    // copied from com.google.common.base.Suppliers.MemoizingSupplier
    private static class MemoizingSupplier<T> implements Supplier<T>, Serializable {
        final Supplier<T> delegate;
        transient volatile boolean initialized;
        // "value" does not need to be volatile; visibility piggy-backs
        // on volatile read of "initialized".
        transient T value;

        MemoizingSupplier(Supplier<T> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public T get() {
            // A 2-field variant of Double Checked Locking.
            if (!initialized) {
                synchronized (this) {
                    if (!initialized) {
                        T t = delegate.get();
                        value = t;
                        initialized = true;
                        return t;
                    }
                }
            }
            return value;
        }

        @Override
        public String toString() {
            return "Suppliers.memoize(" + delegate + ")";
        }

        private static final long serialVersionUID = 0;
    }
}
