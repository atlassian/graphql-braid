package com.atlassian.braid.document;

import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.function.Function;

/**
 * A factory to create {@link DocumentMapper document mappers} from a {@link TypeDefinitionRegistry schema}
 */
public interface DocumentMapperFactory extends Function<TypeDefinitionRegistry, DocumentMapper> {

    /**
     * Adds a  type mapping to the factory
     *
     * @param typeMapper the type mapper to add
     * @return a <em>new</em> {@link DocumentMapperFactory}
     * @see TypeMapper
     * @see TypeMappers
     */
    DocumentMapperFactory mapType(TypeMapper typeMapper);
}
