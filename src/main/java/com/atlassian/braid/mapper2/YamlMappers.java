package com.atlassian.braid.mapper2;

import com.atlassian.braid.collections.Maps;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class YamlMappers {

    @SuppressWarnings("unchecked")
    static Map<String, Object> load(Supplier<Reader> yaml) {
        try (Reader reader = yaml.get()) {
            return new Yaml().loadAs(reader, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            final Map<String, Object> props = Maps.cast(object);
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
                return new CopyOperation<>(Maps::get, sourceKey, sourceKey, () -> null, Function.identity());
            }
        },
        PUT {
            @Override
            MapperOperation getOperation(String sourceKey, Map<String, Object> props) {
                return new PutOperation<>(sourceKey, props.get("value"));
            }
        };

        abstract MapperOperation getOperation(String sourceKey, Map<String, Object> props);
    }
}
