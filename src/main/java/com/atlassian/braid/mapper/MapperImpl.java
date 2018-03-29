package com.atlassian.braid.mapper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.atlassian.braid.mapper.MapperOperations.noop;
import static java.util.Objects.requireNonNull;

/**
 * Default implemenation of {@link Mapper}
 */
final class MapperImpl implements Mapper {

    private final MapperOperation operation;

    MapperImpl() {
        this(noop());
    }

    MapperImpl(MapperOperation operation) {
        this.operation = requireNonNull(operation);
    }

    @Override
    public final Map<String, Object> apply(Map<String, Object> input) {
        final Map<String, Object> output = new HashMap<>();
        operation.accept(input, output);
        return output;
    }

    @Override
    public <T, R> Mapper copy(String sourceKey, String targetKey, Supplier<R> defaultValue, Function<T, R> transform) {
        return newMapper(new CopyOperation<>(sourceKey, targetKey, defaultValue, transform));
    }

    @Override
    public <V> Mapper put(String key, V value) {
        return newMapper(new PutOperation<>(key, value));
    }

    @Override
    public Mapper copyList(String sourceKey, String targetKey, Mapper mapper) {
        return newMapper(new CopyListOperation(sourceKey, targetKey, mapper));
    }

    @Override
    public Mapper list(String key, Predicate<MapperInputOutput> predicate, Mapper mapper) {
        return newMapper(new ListOperation(key, predicate, mapper));
    }

    @Override
    public Mapper map(String key, Predicate<MapperInputOutput> predicate, Function<Map<String, Object>, Map<String, Object>> mapper) {
        return newMapper(new MapOperation(key, predicate, mapper));
    }

    @Override
    public Mapper copyMap(String sourceKey, String targetKey, Mapper mapper) {
        return newMapper(new CopyMapOperation(sourceKey, targetKey, mapper));
    }

    private MapperImpl newMapper(MapperOperation afterOperation) {
        return new MapperImpl(this.operation.andThen(afterOperation));
    }
}
