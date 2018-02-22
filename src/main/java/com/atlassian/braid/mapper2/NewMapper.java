package com.atlassian.braid.mapper2;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.atlassian.braid.mapper2.MapperOperations.composed;
import static java.util.stream.Collectors.toList;

public interface NewMapper extends UnaryOperator<Map<String, Object>> {

    static NewMapper fromYaml(Supplier<Reader> yaml) {
        final List<MapperOperation> operations = YamlMappers.load(yaml)
                .entrySet().stream()
                .map(YamlMappers::fromEntry)
                .collect(toList());
        return new MapperImpl(composed(operations));

    }

    static NewMapper mapper() {
        return new MapperImpl();
    }

    default NewMapper copy(String key) {
        return copy(key, key);
    }

    default <T> NewMapper copy(String key, Supplier<T> defaultValue) {
        return copy(key, key, defaultValue);
    }

    default <T, R> NewMapper copy(String key, Function<T, R> transform) {
        return copy(key, key, () -> null, transform);
    }

    default NewMapper copy(String sourceKey, String targetKey) {
        return copy(sourceKey, targetKey, () -> null);
    }

    default <T> NewMapper copy(String sourceKey, String targetKey, Supplier<T> defaultValue) {
        return copy(sourceKey, targetKey, defaultValue, Function.identity());
    }

    <T, R> NewMapper copy(String sourceKey, String targetKey, Supplier<R> defaultValue, Function<T, R> transform);

    <V> NewMapper put(String key, V value);
}
