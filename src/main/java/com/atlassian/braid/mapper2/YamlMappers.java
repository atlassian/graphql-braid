package com.atlassian.braid.mapper2;

import com.atlassian.braid.collections.BraidObjects;
import com.atlassian.braid.collections.Maps;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.collections.BraidObjects.cast;
import static com.atlassian.braid.mapper2.MapperOperations.composed;
import static com.atlassian.braid.mapper2.NewMapper.mapper;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class YamlMappers {

    @SuppressWarnings("unchecked")
    static Map<String, Object> load(Supplier<Reader> yaml) {
        try (Reader reader = yaml.get()) {
            return new Yaml().loadAs(reader, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static NewMapper newYamlMapper(Map<String, Object> yamlAsMap) {
        return new MapperImpl(composed(toMapperOperations(yamlAsMap)));
    }

    private static List<MapperOperation> toMapperOperations(Map<String, Object> yamlAsMap) {
        return yamlAsMap
                .entrySet().stream()
                .map(YamlMappers::fromEntry)
                .collect(toList());
    }

    static MapperOperation fromEntry(Map.Entry<String, Object> entry) {
        final String sourceKey = entry.getKey();

        final OperationNameAndProps opsAndProps = from(entry.getValue());

        return opsAndProps.getOperation()
                .map(String::toUpperCase)
                .map(YamlOperationType::valueOf)
                .map(o -> o.getOperation(sourceKey, opsAndProps.getProperties()))
                .orElseThrow(IllegalStateException::new);
    }

    static OperationNameAndProps from(Object object) {
        if (object instanceof String) {
            return new OperationNameAndProps(String.valueOf(object), Collections.emptyMap());
        } else if (object instanceof Map) {
            final Map<String, Object> props = cast(object);
            return new OperationNameAndProps(Maps.get(props, "op").map(String::valueOf).orElse(null), props);
        } else {
            return new OperationNameAndProps(null, Collections.emptyMap());
        }
    }

    static class OperationNameAndProps {
        private final String operation;
        private final Map<String, Object> properties;

        OperationNameAndProps(String operation, Map<String, Object> properties) {
            this.operation = operation;
            this.properties = requireNonNull(properties);
        }

        public Optional<String> getOperation() {
            return Optional.ofNullable(operation);
        }

        Map<String, Object> getProperties() {
            return properties;
        }
    }

    enum YamlOperationType {
        COPY {
            @Override
            MapperOperation getOperation(String sourceKey, Map<String, Object> props) {
                return new CopyOperation<>(Maps::get, sourceKey, getTargetKey(props, sourceKey), () -> null, Function.identity());
            }
        },
        PUT {
            @Override
            MapperOperation getOperation(String sourceKey, Map<String, Object> props) {
                return new PutOperation<>(sourceKey, props.get("value"));
            }
        },
        COPYLIST {
            @Override
            MapperOperation getOperation(String sourceKey, Map<String, Object> props) {
                return Maps.get(props, "mapper")
                        .map(BraidObjects::<Map<String, Object>>cast)
                        .map(mapper -> new CopyListOperation(sourceKey, getTargetKey(props, sourceKey), newYamlMapper(mapper)))
                        .orElseGet(() -> new CopyListOperation(sourceKey, sourceKey, mapper()));
            }
        };

        abstract MapperOperation getOperation(String sourceKey, Map<String, Object> props);
    }

    private static String getTargetKey(Map<String, Object> props, String defaultValue) {
        return Maps.get(props, "target").map(String::valueOf).orElse(defaultValue);
    }
}
