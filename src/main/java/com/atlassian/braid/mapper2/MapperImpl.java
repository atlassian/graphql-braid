package com.atlassian.braid.mapper2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.mapper2.MapperOperations.noop;
import static java.util.Objects.requireNonNull;

final class MapperImpl implements NewMapper {

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
    public <T, R> NewMapper copy(String sourceKey, String targetKey, Supplier<R> defaultValue, Function<T, R> transform) {
        return newMapper(new CopyOperation<>(sourceKey, targetKey, defaultValue, transform));
    }

    @Override
    public <V> NewMapper put(String key, V value) {
        return newMapper(new PutOperation<>(key, value));
    }

    @Override
    public NewMapper copyList(String sourceKey, String targetKey, NewMapper mapper) {
        return newMapper(new CopyListOperation(sourceKey, targetKey, mapper));
    }

    @Override
    public NewMapper list(String key, NewMapper mapper) {
        return newMapper(new ListOperation(key, mapper));
    }

    @Override
    public NewMapper map(String key, NewMapper mapper) {
        return newMapper(new MapOperation(key, mapper));
    }

    @Override
    public NewMapper copyMap(String sourceKey, String targetKey, NewMapper mapper) {
        return newMapper(new CopyMapOperation(sourceKey, targetKey, mapper));
    }

    private MapperImpl newMapper(MapperOperation afterOperation) {
        return new MapperImpl(this.operation.andThen(afterOperation));
    }

    private static Optional<Object> getFromMap(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key));
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object o) {
        return (T) o;
    }
}
