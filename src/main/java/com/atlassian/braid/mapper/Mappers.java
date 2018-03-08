package com.atlassian.braid.mapper;

import java.io.Reader;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.atlassian.braid.mapper.MapperOperations.composed;
import static com.atlassian.braid.mapper.YamlMappers.toMapperOperations;

/**
 * Main entry class to create {@link Mapper mappers} from different <em>source</em>, whether it's in code directly or
 * through Yaml configuration
 */
public final class Mappers {

    private Mappers() {
    }

    public static Mapper mapper() {
        return new MapperImpl();
    }

    public static Mapper fromYaml(Supplier<Reader> yaml) {
        return fromYamlMap(YamlMappers.load(yaml));
    }

    public static Mapper fromYamlMap(Map<String, Object> yamlAsMap) {
        return new MapperImpl(composed(toMapperOperations(yamlAsMap)));
    }

    /**
     * Predicates that returns {@code true} if the mapper input contains a value at the given key
     *
     * @param key the key to lookup
     * @return a predicate
     * @see Mapper#list(String, Predicate, Mapper)
     * @see Mapper#map(String, Predicate, Mapper)
     */
    public static Predicate<MapperInputOutput> inputContains(String key) {
        return inout -> MapperMaps.get(inout.getInput(), key).isPresent();
    }
}
