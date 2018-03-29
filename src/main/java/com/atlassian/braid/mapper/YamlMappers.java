package com.atlassian.braid.mapper;

import com.atlassian.braid.yaml.BraidYaml;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.java.util.BraidMaps.firstEntry;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.yaml.BraidYaml.getKey;
import static com.atlassian.braid.yaml.BraidYaml.getOperationName;
import static com.atlassian.braid.yaml.BraidYaml.getTargetKey;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Helper class to help build mappers from Yaml configuration
 */
final class YamlMappers {

    static List<MapperOperation> toMapperOperations(List<Map<String, Object>> yamlAsList) {
        return yamlAsList
                .stream()
                .map(YamlMappers::toYamlOperation)
                .map(YamlMapperOperation::get)
                .collect(toList());
    }

    private static YamlMapperOperation toYamlOperation(Map<String, Object> yaml) {
        if (yaml.size() == 1) {
            final Map.Entry<String, Object> operationAsEntry = firstEntry(yaml);
            assert operationAsEntry != null;
            return new YamlMapperOperation(operationAsEntry.getKey(), cast(operationAsEntry.getValue()), emptyMap());
        } else {
            return new YamlMapperOperation(
                    getKey(yaml, MapperException::new),
                    getOperationName(yaml, MapperException::new),
                    yaml);
        }
    }

    // those are mapped dynamically, see #operationFromEntry above
    @SuppressWarnings("unused")
    enum YamlOperationType implements BiFunction<String, Map<String, Object>, MapperOperation> {
        COPY((key, props) -> new CopyOperation<>(key, getTargetKey(props, key), () -> null, Function.identity())),
        PUT((key, props) -> new PutOperation<>(key, props.get("value"))),
        COPYLIST((key, props) -> new CopyListOperation(key, getTargetKey(props, key), getMapper(props))),
        LIST((key, props) -> new ListOperation(key, __ -> true, getMapper(props))),
        MAP((key, props) -> new MapOperation(key, __ -> true, getMapper(props))),
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

    private static Mapper getMapper(Map<String, Object> props) {
        return BraidYaml.getMapper(props)
                .map(Mappers::fromYamlList)
                .orElseGet(Mappers::mapper);
    }

    private static class YamlMapperOperation implements Supplier<MapperOperation> {
        private final String key;
        private final String name;
        private final Map<String, Object> props;

        private YamlMapperOperation(String key, String name, Map<String, Object> props) {
            this.key = requireNonNull(key);
            this.name = requireNonNull(name);
            this.props = requireNonNull(props);
        }

        private YamlOperationType getOperation() {
            try {
                return YamlOperationType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new MapperException("Could not find operation with name '%s'", name);
            }
        }

        @Override
        public MapperOperation get() {
            return getOperation().apply(key, props);
        }
    }
}
