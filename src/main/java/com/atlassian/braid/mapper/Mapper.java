package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Mapper interface to build new mappers, note that mapper can be <em>applied</em> safely on multiple map intances
 */
public interface Mapper extends UnaryOperator<Map<String, Object>> {

    /**
     * Copies the value at a key to the same key in the target map
     *
     * @param key The key to copy
     * @return the mapper with the copy operation, this is <em>not</em> necessarily the same mapper
     */
    default Mapper copy(String key) {
        return copy(key, key);
    }

    /**
     * Copies the value at a key to the same key in the target map if present, otherwise puts the supplied default value
     * in the target map
     *
     * @param key The key to copy
     * @param <T> the type of the (default) value
     * @return the mapper with the copy operation, this is <em>not</em> necessarily the same mapper
     */
    default <T> Mapper copy(String key, Supplier<T> defaultValue) {
        return copy(key, key, defaultValue);
    }

    /**
     * Copies the value at a key to the same key in the target map after having applied the given transformation to the
     * found value
     *
     * @param key       the key to copy
     * @param transform the function to transform the value
     * @param <T>       the original type of the value
     * @param <R>       the type of the transformed value
     * @return the mapper with the copy operation, this is <em>not</em> necessarily the same mapper
     */
    default <T, R> Mapper copy(String key, Function<T, R> transform) {
        return copy(key, key, () -> null, transform);
    }

    /**
     * Copies the value found via an expression to a new key in the target map
     *
     * @param sourceKey the expression to use to find the source value
     * @param targetKey the target key
     * @return the mapper with the copy operation, this is <em>not</em> necessarily the same mapper
     */
    default Mapper copy(String sourceKey, String targetKey) {
        return copy(sourceKey, targetKey, () -> null);
    }

    /**
     * Copies the value found via an expression to a new key in the target map if present, otherwise puts the supplied
     * default value in the target map
     *
     * @param sourceKey    the expression to use to find the source value
     * @param targetKey    the target key
     * @param defaultValue the supplier of default value
     * @param <T>          the type of the (default) value
     * @return the mapper with the copy operation, this is <em>not</em> necessarily the same mapper
     */
    default <T> Mapper copy(String sourceKey, String targetKey, Supplier<T> defaultValue) {
        return copy(sourceKey, targetKey, defaultValue, Function.identity());
    }

    /**
     * Copies the value found via an expression to a new key in the target map after having applied the given
     * transformation to the found value, if no value is found puts the supplied defaut value in the target map
     *
     * @param sourceKey    the expression to use to find the source value
     * @param targetKey    the target key
     * @param transform    the function to transform the value
     * @param defaultValue the supplier of default value
     * @param <T>          the original type of the value
     * @param <R>          the type of the transformed value
     * @return the mapper with the copy operation, this is <em>not</em> necessarily the same mapper
     */
    <T, R> Mapper copy(String sourceKey, String targetKey, Supplier<R> defaultValue, Function<T, R> transform);

    /**
     * Puts a value at a key
     *
     * @param key   the key
     * @param value the value
     * @return the mapper with the put operation, this is <em>not</em> necessarily the same mapper
     */
    <V> Mapper put(String key, V value);

    /**
     * Copies a list from the source key to the same key
     *
     * @param sourceKey the expression to use to find the source list
     * @param mapper    the mapper for each item in the list
     * @return the mapper with the copyList operation, this is <em>not</em> necessarily the same mapper
     */
    default Mapper copyList(String sourceKey, Mapper mapper) {
        return copyList(sourceKey, sourceKey, mapper);
    }


    /**
     * Copies a list from the source key to the new key
     *
     * @param sourceKey the expression to use to find the source list
     * @param targetKey the new key
     * @param mapper    the mapper for each item in the list
     * @return the mapper with the copyList operation, this is <em>not</em> necessarily the same mapper
     */
    Mapper copyList(String sourceKey, String targetKey, Mapper mapper);

    /**
     * Creates a list of a single map
     *
     * @param key    the key to store the list at
     * @param mapper a mapper for the new map
     * @return the mapper with the list operation, this is <em>not</em> necessarily the same mapper
     */
    default Mapper list(String key, Mapper mapper) {
        return list(key, __ -> true, mapper);
    }

    /**
     * Creates a list of a single map
     *
     * @param key    the key to store the list at
     * @param mapper a mapper for the new map
     * @return the mapper with the list operation, this is <em>not</em> necessarily the same mapper
     */
    Mapper list(String key, Predicate<MapperInputOutput> predicate, Mapper mapper);

    /**
     * Creates a new map at a given key
     *
     * @param key    the key
     * @param mapper a mapper for the new map
     * @return the mapper with the map operation, this is <em>not</em> necessarily the same mapper
     */
    default Mapper map(String key, Mapper mapper) {
        return map(key, __ -> true, mapper);
    }

    /**
     * Creates a new map at a given key
     *
     * @param key    the key
     * @param mapper a mapper for the new map
     * @return the mapper with the map operation, this is <em>not</em> necessarily the same mapper
     */
    Mapper map(String key, Predicate<MapperInputOutput> predicate, Mapper mapper);

    /**
     * Copies a map at a key to the same key in the target map
     *
     * @param sourceKey The key to copy
     * @param mapper    a mapper for the map
     * @return the mapper with the map operation, this is <em>not</em> necessarily the same mapper
     */
    default Mapper copyMap(String sourceKey, Mapper mapper) {
        return copyMap(sourceKey, sourceKey, mapper);
    }

    /**
     * Copies a map at a key to the new key in the target map
     *
     * @param sourceKey The key to copy
     * @param targetKey the new key
     * @param mapper    a mapper for the map
     * @return the mapper with the map operation, this is <em>not</em> necessarily the same mapper
     */
    Mapper copyMap(String sourceKey, String targetKey, Mapper mapper);
}
