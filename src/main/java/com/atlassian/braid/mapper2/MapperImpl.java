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
        return newMapper(new CopyOperation<>(MapperImpl::getFromMap, sourceKey, targetKey, defaultValue, transform));
    }

    @Override
    public <V> NewMapper put(String key, V value) {
        return newMapper(new PutOperation<>(key, value));
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
