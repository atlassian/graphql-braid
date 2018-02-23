package com.atlassian.braid.mapper2;

import java.io.Reader;
import java.util.Map;
import java.util.function.Supplier;

import static com.atlassian.braid.mapper2.MapperOperations.composed;
import static com.atlassian.braid.mapper2.YamlMappers.toMapperOperations;

public final class Mappers {

    private Mappers() {
    }

    public static NewMapper mapper() {
        return new MapperImpl();
    }

    public static NewMapper fromYaml(Supplier<Reader> yaml) {
        return fromYamlMap(YamlMappers.load(yaml));
    }

    public static NewMapper fromYamlMap(Map<String, Object> yamlAsMap) {
        return new MapperImpl(composed(toMapperOperations(yamlAsMap)));
    }
}
