package com.atlassian.braid.document;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.atlassian.braid.document.DocumentMapperPredicates.typeNamed;
import static com.atlassian.braid.java.util.BraidMaps.firstEntry;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.yaml.BraidYaml.getKey;
import static com.atlassian.braid.yaml.BraidYaml.getOperationName;
import static com.atlassian.braid.yaml.BraidYaml.getStringValue;
import static com.atlassian.braid.yaml.BraidYaml.getTargetKey;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

/**
 * mapper:
 * - type: Foo
 * operations:
 * - key: id
 * op: copy
 * target: fooId
 * - name: copy
 */

final class YamlTypeMappers {
    private YamlTypeMappers() {
    }

    static List<TypeMapper> from(List<Map<String, Object>> yamlAsList) {
        return yamlAsList
                .stream()
                .map(YamlTypeMappers::toYamlTypeMapper)
                .map(YamlTypeMappers::fromYamlTypeMapper)
                .collect(toList());
    }

    private static YamlTypeMapper toYamlTypeMapper(Map<String, Object> yaml) {
        final String type = getStringValue(yaml, "type", DocumentMapperException::new);
        final List<YamlFieldOperation> operations = BraidMaps.get(yaml, "operations")
                .map(BraidObjects::<List<Map<String, Object>>>cast)
                .map(YamlTypeMappers::toYamlFieldOperations)
                .orElse(emptyList());

        return new YamlTypeMapper(type, operations);
    }

    private static List<YamlFieldOperation> toYamlFieldOperations(List<Map<String, Object>> yaml) {
        return yaml.stream().map(YamlTypeMappers::toYamlFieldOperation).collect(toList());
    }

    private static YamlFieldOperation toYamlFieldOperation(Map<String, Object> yaml) {
        if (yaml.size() == 1) {
            final Map.Entry<String, Object> operationAsEntry = firstEntry(yaml);
            assert operationAsEntry != null;
            return new YamlFieldOperation(
                    operationAsEntry.getKey(),
                    cast(operationAsEntry.getValue()),
                    emptyMap());
        } else {
            return new YamlFieldOperation(
                    getKey(yaml, DocumentMapperException::new),
                    getOperationName(yaml, DocumentMapperException::new),
                    yaml);
        }
    }

    private static TypeMapper fromYamlTypeMapper(YamlTypeMapper yamlTypeMapper) {
        return new TypeMapperImpl(
                typeNamed(yamlTypeMapper.typeName),
                fromYamlFieldOperations(yamlTypeMapper.operations));
    }

    private static List<SelectionOperation> fromYamlFieldOperations(List<YamlFieldOperation> operations) {
        return operations.stream().map(YamlFieldOperation::get).collect(toList());
    }

    @SuppressWarnings("unused") // those are mapped dynamically
    private enum YamlFieldOperationType implements BiFunction<String, Map<String, Object>, SelectionOperation> {
        COPY((key, props) -> new CopyFieldOperation(key, getTargetKey(props, key))),
        PUT((key, props) -> new PutFieldOperation(key, cast(props.get("value"))));

        private final BiFunction<String, Map<String, Object>, SelectionOperation> getOperation;

        YamlFieldOperationType(BiFunction<String, Map<String, Object>, SelectionOperation> getOperation) {
            this.getOperation = getOperation;
        }

        @Override
        public SelectionOperation apply(String key, Map<String, Object> props) {
            return getOperation.apply(key, props);
        }
    }

    private static class YamlTypeMapper {
        private final String typeName;
        private final List<YamlFieldOperation> operations;

        YamlTypeMapper(String typeName, List<YamlFieldOperation> operations) {
            this.typeName = typeName;
            this.operations = operations;
        }
    }

    private static class YamlFieldOperation implements Supplier<SelectionOperation> {

        private final String key;
        private final String name;
        private final Map<String, Object> props;

        YamlFieldOperation(String key, String name, Map<String, Object> props) {
            this.key = key;
            this.name = name;
            this.props = props;
        }


        private YamlFieldOperationType getOperation() {
            try {
                return YamlFieldOperationType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new DocumentMapperException("Could not find operation with name '%s'", name);
            }
        }

        @Override
        public SelectionOperation get() {
            return getOperation().apply(key, props);
        }
    }
}
