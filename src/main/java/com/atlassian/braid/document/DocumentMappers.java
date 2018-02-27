package com.atlassian.braid.document;

import com.atlassian.braid.yaml.BraidYaml;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class DocumentMappers {

    private DocumentMappers() {
    }

    public static DocumentMapper noop() {
        return doc -> new DocumentMapper.MappedDocument(doc, Function.identity());
    }

    public static Function<TypeDefinitionRegistry, DocumentMapper> identity() {
        return registry -> noop();
    }

    public static Function<TypeDefinitionRegistry, DocumentMapper> fromYaml(Supplier<Reader> yaml) {
        return fromYamlList(BraidYaml.loadAsList(yaml));
    }

    public static Function<TypeDefinitionRegistry, DocumentMapper> fromYamlList(List<Map<String, Object>> yamlAsList) {
        return registry -> new TypedDocumentMapper(registry, YamlTypeMappers.from(yamlAsList));
    }
}
