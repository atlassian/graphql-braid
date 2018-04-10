package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.atlassian.braid.java.util.BraidLists.concat;
import static com.atlassian.braid.java.util.BraidObjects.cast;

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

    static Map<String, Object> mergeMaps(Map<String, Object> map1, Map<String, Object> map2) {
        if (map1.isEmpty()) {
            return map2;
        }
        if (map2.isEmpty()) {
            return map1;
        }

        final Map<String, Object> merged = new HashMap<>();
        merged.putAll(map1);
        map2.forEach((key, value) -> mergeEntry(merged, key, value));

        return merged;
    }

    private static void mergeEntry(Map<String, Object> merged, String key, Object value) {
        if (!merged.containsKey(key)) {
            merged.put(key, value);
        } else {
            merged.put(key, mergeValues(merged.get(key), value));
        }
    }

    private static Object mergeValues(Object value1, Object value2) {
        if (value1 instanceof Map && value2 instanceof Map) {
            return mergeMaps(cast(value1), cast(value2));
        } else if (value1 instanceof List && value2 instanceof List) {
            return concat(BraidObjects.<List>cast(value1), BraidObjects.<List>cast(value2));
        } else {
            throw new IllegalStateException("Can't merge values, getting non-mergeable types");
        }
    }

    private static <T> T newInstance(String name)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return cast(Mapper.class.getClassLoader().loadClass(name).newInstance());
    }
}