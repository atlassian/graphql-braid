package com.atlassian.braid.document;

import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Interface to define type mappers that applies a series of operation to a given type
 *
 * @see TypeMapper
 */
public interface TypeMapper extends Predicate<ObjectTypeDefinition>, BiFunction<MappingContext, SelectionSet, SelectionSetMappingResult> {

    TypeMapper copy(String key, String target);

    TypeMapper copyRemaining();

    TypeMapper put(String key, String value);
}
