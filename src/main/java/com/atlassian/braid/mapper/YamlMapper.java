package com.atlassian.braid.mapper;

import com.atlassian.braid.mapper.operation.CopyFieldOperation;
import com.atlassian.braid.mapper.operation.CopyListFieldOperation;
import com.atlassian.braid.mapper.operation.CopyMapFieldOperation;
import com.atlassian.braid.mapper.operation.MapFieldOperation;
import com.atlassian.braid.mapper.operation.PutFieldOperation;
import com.atlassian.braid.mapper.operation.SingletonListFieldOperation;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

/**
 * A builder of a mapper configured in YAML
 */
public class YamlMapper {

    private static final Map<String, Function<FieldOperationSource, FieldOperation>> fieldOperations =
            new HashMap<String, Function<FieldOperationSource, FieldOperation>>() {{
                put("copy", src -> new CopyFieldOperation(src.getName(), src.getProperties(), src.getOps()));
                put("copyList", src -> new CopyListFieldOperation(src.getName(), src.getProperties(), src.getOps()));
                put("singletonList", src -> new SingletonListFieldOperation(src.getName(), src.getOps()));
                put("put", src -> new PutFieldOperation(src.getName(), src.getProperties(), src.getOps()));
                put("map", src -> new MapFieldOperation(src.getName(), src.getOps()));
                put("copyMap", src -> new CopyMapFieldOperation(src.getName(), src.getProperties(), src.getOps()));
            }};
    private final List<FieldOperation> operations;

    public YamlMapper(Reader yaml) {
        this((Map<String, Object>)new Yaml().load(yaml));
    }

    public YamlMapper(Map<String, Object> yaml) {
        this.operations = createFieldOperations(yaml);
    }

    public Map<String, Object> map(Map<String, Object> source){
        Mapper mapper = Mapper.newMapper(source);

        process(mapper, operations);
        return mapper.build();
    }

    private void process(Mapper mapper, List<FieldOperation> operations) {
        for (FieldOperation op : operations) {
            op.execute(mapper);
        }
    }

    private List<FieldOperation> createFieldOperations(Map<String, Object> elements) {
        if (elements == null) {
            return emptyList();
        }

        return elements.entrySet().stream().map(e -> {
            String fieldName = e.getKey();
            String op;
            Map<String, Object> args;
            if (e.getValue() instanceof Map) {
                args = (Map<String, Object>) e.getValue();
                op = (String) args.get("op");
            } else {
                op = (String) e.getValue();
                args = emptyMap();
            }
            List<FieldOperation> ops = createFieldOperations((Map<String, Object>) args.get("elements"));
            return Optional.ofNullable(fieldOperations.get(op))
                    .orElseThrow(() -> new IllegalArgumentException(format("Invalid operation %s for field %s", op, fieldName)))
                    .apply(new FieldOperationSource(fieldName, args, ops));
        }).collect(toList());
    }

    private static class FieldOperationSource {
        private final String name;
        private final Map<String, Object> properties;
        private final List<FieldOperation> ops;

        public FieldOperationSource(String name, Map<String, Object> properties, List<FieldOperation> ops) {
            this.name = name;
            this.properties = properties;
            this.ops = ops;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public List<FieldOperation> getOps() {
            return ops;
        }
    }

}
