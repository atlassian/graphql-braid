package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.BiConsumer;

import static com.atlassian.braid.mapper.MapperOperations.composed;

/**
 * Implementation of a single {@link Mapper} operation, there is basically one implementation per mapper method.
 */
public interface MapperOperation extends BiConsumer<Map<String, Object>, Map<String, Object>> {

    void accept(Map<String, Object> input, Map<String, Object> output);

    default MapperOperation andThen(MapperOperation after) {
        return composed(this, after);
    }

//    MapperOperation toPath(String... path);
}
