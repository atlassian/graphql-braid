package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Useful class to work with maps, and notably leverage SpringExpressions if present
 */
final class MapperMaps {

    private static BiFunction<Map<String, Object>, String, Optional<Object>> getFromMap;

    static {
        try {
            getFromMap = MapperMaps.<SpringExpressions>newInstance("com.atlassian.braid.mapper.SpringExpressions")::get;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            LoggerFactory.getLogger(CopyOperation.class).debug("Spring not found, using simple property expressions", e);
            getFromMap = BraidMaps::get;
        }
    }

    private MapperMaps() {
    }

    public static <V> Optional<V> get(Map<String, Object> map, String key) {
        return getFromMap.apply(map, key).map(BraidObjects::cast);
    }

    private static <T> T newInstance(String name)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return BraidObjects.cast(Mapper.class.getClassLoader().loadClass(name).newInstance());
    }
}