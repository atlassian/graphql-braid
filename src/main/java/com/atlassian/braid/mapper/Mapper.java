package com.atlassian.braid.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Maps fields from one map to another
 */
public interface Mapper {
    /**
     * Copies the value at a key to the same key in the target map
     *
     * @param key The key to copy
     * @return the current mapper
     */
    Mapper copy(String key);

    /**
     * Copies the value at a key to the same key in the target map after having applied the given transformation to the
     * found value
     *
     * @param key       the key to copy
     * @param transform the function to transform the value
     * @param <T>       the original type of the value
     * @param <R>       the type of the transformed value
     * @return the current mapper
     */
    <T, R> Mapper copy(String key, Function<T, R> transform);

    /**
     * Copies the value at a key to the same key in the target map if present, otherwise puts the supplied defaut value
     * in the target map
     *
     * @param key The key to copy
     * @param <T> the type of the (default) value
     * @return the current mapper
     */
    <T> Mapper copy(String key, Supplier<T> defaultValue);

    /**
     * Copies the value at a key to the same key in the target map after having applied the given transformation to the
     * found value if no value is found puts the supplied defaut value in the target map
     *
     * @param key          the key to copy
     * @param transform    the function to transform the value
     * @param defaultValue the supplier of default value
     * @param <T>          the original type of the value
     * @param <R>          the type of the transformed value
     * @return the current mapper
     */
    <T, R> Mapper copy(String key, Function<T, R> transform, Supplier<R> defaultValue);

    /**
     * Copies the value found via an expression to a new key in the target map
     *
     * @param sourcePath the expression to use to find the source value
     * @param targetKey  the target key
     * @return the current mapper
     */
    Mapper copy(String sourcePath, String targetKey);

    /**
     * Copies the value found via an expression to a new key in the target map if present, otherwise puts the supplied
     * defaut value in the target map
     *
     * @param sourcePath   the expression to use to find the source value
     * @param targetKey    the target key
     * @param defaultValue the supplier of default value
     * @param <T>          the type of the (default) value
     * @return the current mapper
     */
    <T> Mapper copy(String sourcePath, String targetKey, Supplier<T> defaultValue);

    /**
     * Copies the value found via an expression to a new key in the target map after having applied the given
     * transformation to the found value
     *
     * @param sourcePath the expression to use to find the source value
     * @param targetKey  the target key
     * @param transform  the function to transform the value
     * @param <T>        the original type of the value
     * @param <R>        the type of the transformed value
     * @return the current mapper
     */
    <T, R> Mapper copy(String sourcePath, String targetKey, Function<T, R> transform);

    /**
     * Copies the value found via an expression to a new key in the target map after having applied the given
     * transformation to the found value, if no value is found puts the supplied defaut value in the target map
     *
     * @param sourcePath   the expression to use to find the source value
     * @param targetKey    the target key
     * @param transform    the function to transform the value
     * @param defaultValue
     * @param <T>          the original type of the value
     * @param <R>          the type of the transformed value
     * @return the current mapper
     */
    <T, R> Mapper copy(String sourcePath, String targetKey, Function<T, R> transform, Supplier<R> defaultValue);

    /**
     * Copies a map at a key to the same key in the target map
     *
     * @param key The key to copy
     * @return the current mapper
     */
    Mapper copyMap(String key);

    /**
     * Copies a map at a key to a new key in the target map
     *
     * @param sourcePath the expression to use to find the source value
     * @param key The key to copy
     * @return the current mapper
     */
    Mapper copyMap(String sourcePath, String key);

    /**
     * Creates a new map at a given key
     *
     * @param key the key
     * @return a mapper for the new map
     */
    Mapper map(String key);

    /**
     * Creates a list of a single map
     *
     * @param key the key to store the list at
     * @return a mapper for the new map
     */
    Mapper singletonList(String key);

    /**
     * Puts a value at a key
     *
     * @param key   the key
     * @param value the value
     * @return the current mapper
     */
    Mapper put(String key, Object value);

    /**
     * Copies a list from the source expression to the new key
     *
     * @param sourcePath the expression to use to find the source list
     * @param targetKey  the target key
     * @return a mapper for each item in the list
     */
    Mapper copyList(String sourcePath, String targetKey);

    /**
     * Copies a list from the source key to the map at the same key
     *
     * @param key the key
     * @return a mapper for each item in the list
     */
    Mapper copyList(String key);

    /**
     * Marks the mapper done.  Used for sub mappers like those returned from {@link #copyList(String)}
     *
     * @return the parent mapper
     */
    Mapper done();

    /**
     * Builds a map using the previous rules
     *
     * @return the new map
     */
    Map<String, Object> build();

    /**
     * Builds a new mapper
     *
     * @param data the source map
     * @return the mapper
     */
    static Mapper newMapper(Map<String, Object> data) {
        Logger log = LoggerFactory.getLogger(Mapper.class);
        ExpressionEvaluator evaluator;
        try {
            Class cls = Mapper.class.getClassLoader().loadClass("com.atlassian.braid.mapper.SpringExpressionEvaluator");
            evaluator = (ExpressionEvaluator) cls.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            log.debug("Spring not found, using simple property expressions");
            evaluator = new SimpleExpressionEvaluator();
        }
        return new DefaultMapper(evaluator, data);
    }
}

