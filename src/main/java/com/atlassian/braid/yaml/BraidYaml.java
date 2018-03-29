package com.atlassian.braid.yaml;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class BraidYaml {

    private BraidYaml() {
    }

    public static Map<String, Object> loadAsMap(Supplier<Reader> yaml) {
        return BraidObjects.cast(load(yaml, Map.class));
    }

    public static List<Map<String, Object>> loadAsList(Supplier<Reader> yaml) {
        return BraidObjects.cast(load(yaml, List.class));
    }

    private static <T> T load(Supplier<Reader> yaml, Class<T> type) {
        try (Reader reader = yaml.get()) {
            return new Yaml().loadAs(reader, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getKey(Map<String, Object> props,
                                BiFunction<String, Object[], ? extends RuntimeException> exception) {
        return getStringValue(props, "key", exception);
    }

    public static String getOperationName(Map<String, Object> props,
                                          BiFunction<String, Object[], ? extends RuntimeException> exception) {
        return getStringValue(props, "op", exception);
    }

    public static String getStringValue(Map<String, Object> props, String key, BiFunction<String, Object[], ? extends RuntimeException> exception) {
        return BraidMaps.get(props, key)
                .map(String::valueOf)
                .orElseThrow(() -> exception.apply("Could not find attribute (%s) for configuration: %s", new Object[]{key, props}));
    }

    public static String getTargetKey(Map<String, Object> props, String defaultValue) {
        return BraidMaps.get(props, "target")
                .map(String::valueOf)
                .orElse(defaultValue);
    }

    public static Optional<List<Map<String, Object>>> getMapper(Map<String, Object> props) {
        return BraidMaps.get(props, "mapper")
                .map(BraidObjects::<List<Map<String, Object>>>cast);
    }

    public static final class OperationNameAndProps {
        private final String operation;
        private final Map<String, Object> properties;

        public OperationNameAndProps(String operation, Map<String, Object> properties) {
            this.operation = operation;
            this.properties = requireNonNull(properties);
        }

        public Optional<String> getOperation() {
            return Optional.ofNullable(operation);
        }

        public Map<String, Object> getProperties() {
            return properties;
        }
    }
}
