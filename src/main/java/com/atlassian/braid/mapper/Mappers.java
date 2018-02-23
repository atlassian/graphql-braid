package com.atlassian.braid.mapper;

import java.io.Reader;
import java.util.Map;
import java.util.function.Supplier;

import static com.atlassian.braid.mapper.MapperOperations.composed;
import static com.atlassian.braid.mapper.YamlMappers.toMapperOperations;

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
}
