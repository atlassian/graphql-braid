package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A convenient class to link the input and output of a {@link Mapper}
 * Useful for example in predicates at {@link Mapper#list(String, Predicate, Mapper)}
 * or {@link Mapper#map(String, Predicate, Function)}
 */
public interface MapperInputOutput {

    /**
     * Gets the unmodifiable input
     *
     * @return the input map
     */
    Map<String, Object> getInput();

    /**
     * Gets the unmodifiable output
     *
     * @return the output map
     */
    Map<String, Object> getOutput();
}
