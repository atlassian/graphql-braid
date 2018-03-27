package com.atlassian.braid.document;

import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;

import static com.atlassian.braid.java.util.BraidLists.concat;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * <strong>Internal</strong> implementation of the {@link DocumentMapperFactory}
 *
 * @see DocumentMapperFactory
 * @see TypeMapper
 * @see TypeMappers
 */
class TypedDocumentMapperFactoryFactory implements DocumentMapperFactory {

    private final List<TypeMapper> typeMappers;

    TypedDocumentMapperFactoryFactory() {
        this(emptyList());
    }

    TypedDocumentMapperFactoryFactory(List<TypeMapper> typeMappers) {
        this.typeMappers = new ArrayList<>(requireNonNull(typeMappers));
    }

    @Override
    public DocumentMapperFactory mapType(TypeMapper typeMapper) {
        return new TypedDocumentMapperFactoryFactory(concat(typeMappers, typeMapper));
    }

    @Override
    public DocumentMapper apply(TypeDefinitionRegistry schema) {
        return new TypedDocumentMapper(schema, typeMappers);
    }
}
