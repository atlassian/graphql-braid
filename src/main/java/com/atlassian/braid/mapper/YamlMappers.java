package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Helper class to help build mappers from Yaml configuration
 */
final class YamlMappers {

    @SuppressWarnings("unchecked")
    static Map<String, Object> load(Supplier<Reader> yaml) {
        try (Reader reader = yaml.get()) {
            return new Yaml().loadAs(reader, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<MapperOperation> toMapperOperations(Map<String, Object> yamlAsMap) {
        return yamlAsMap
                .entrySet().stream()
                .map(YamlMappers::operationFromEntry)
                .collect(toList());
    }

    private static MapperOperation operationFromEntry(Map.Entry<String, Object> entry) {
        final String sourceKey = entry.getKey();

        final OperationNameAndProps opsAndProps = toOperationNameAndProps(entry.getValue());

        return opsAndProps.getOperation()
                .map(String::toUpperCase)
                .map(YamlOperationType::valueOf)
                .map(yot -> yot.apply(sourceKey, opsAndProps.getProperties()))
                .orElseThrow(() -> new MapperException("Could not find operation with name '%s'", opsAndProps.operation));
    }

    private static OperationNameAndProps toOperationNameAndProps(Object object) {
        if (object instanceof String) {
            return new OperationNameAndProps(String.valueOf(object), emptyMap());
        } else if (object instanceof Map) {
            final Map<String, Object> props = cast(object);
            return new OperationNameAndProps(getOperationName(props), props);
        } else {
            return new OperationNameAndProps(null, emptyMap());
        }
    }

    // those are mapped dynamically, see #operationFromEntry above
    @SuppressWarnings("unused")
    enum YamlOperationType implements BiFunction<String, Map<String, Object>, MapperOperation> {
        COPY((key, props) -> new CopyOperation<>(key, getTargetKey(props, key), () -> null, Function.identity())),
        PUT((key, props) -> new PutOperation<>(key, props.get("value"))),
        COPYLIST((key, props) -> new CopyListOperation(key, getTargetKey(props, key), getMapper(props))),
        LIST((key, props) -> new ListOperation(key, getMapper(props))),
        MAP((key, props) -> new MapOperation(key, getMapper(props))),
        COPYMAP((key, props) -> new CopyMapOperation(key, getTargetKey(props, key), getMapper(props)));

        final BiFunction<String, Map<String, Object>, MapperOperation> getOperation;

        YamlOperationType(BiFunction<String, Map<String, Object>, MapperOperation> getOperation) {
            this.getOperation = getOperation;
        }

        @Override
        public MapperOperation apply(String key, Map<String, Object> props) {
            return getOperation.apply(key, props);
        }
    }

    static class OperationNameAndProps {
        private final String operation;
        private final Map<String, Object> properties;

        OperationNameAndProps(String operation, Map<String, Object> properties) {
            this.operation = operation;
            this.properties = requireNonNull(properties);
        }

        Optional<String> getOperation() {
            return Optional.ofNullable(operation);
        }

        Map<String, Object> getProperties() {
            return properties;
        }
    }

    private static String getOperationName(Map<String, Object> props) {
        return BraidMaps.get(props, "op")
                .map(String::valueOf)
                .orElseThrow(() -> new MapperException("Could not find attribute (%s) for configuration: %s", "op", props));
    }

    private static String getTargetKey(Map<String, Object> props, String defaultValue) {
        return BraidMaps.get(props, "target")
                .map(String::valueOf)
                .orElse(defaultValue);
    }

    private static Mapper getMapper(Map<String, Object> props) {
        return BraidMaps.get(props, "mapper")
                .map(BraidObjects::<Map<String, Object>>cast)
                .map(Mappers::fromYamlMap)
                .orElseGet(Mappers::mapper);
    }
}
