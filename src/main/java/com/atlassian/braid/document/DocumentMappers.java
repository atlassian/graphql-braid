package com.atlassian.braid.document;

import com.atlassian.braid.yaml.BraidYaml;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper class to build {@link DocumentMapper document mappers}
 * and {@link DocumentMapperFactory document mapper factories}
 *
 * @see DocumentMapper
 * @see DocumentMapperFactory
 */
public final class DocumentMappers {

    private DocumentMappers() {
    }

    public static DocumentMapper noop() {
        return doc -> new DocumentMapper.MappedDocument(doc, Function.identity());
    }

    public static DocumentMapperFactory identity() {
        return factory();
    }

    public static DocumentMapperFactory factory() {
        return new TypedDocumentMapperFactoryFactory() {
            @Override
            public DocumentMapper apply(TypeDefinitionRegistry schema) {
                return noop();
            }
        };
    }

    public static DocumentMapperFactory fromYaml(Supplier<Reader> yaml) {
        return fromYamlList(BraidYaml.loadAsList(yaml));
    }

    public static DocumentMapperFactory fromYamlList(List<Map<String, Object>> yamlAsList) {
        return new TypedDocumentMapperFactoryFactory(YamlTypeMappers.from(yamlAsList));
    }
}
