package com.atlassian.braid.document;

import graphql.language.Document;

import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Mapper interface to build new document mappers
 */
public interface DocumentMapper extends Function<Document, DocumentMapper.MappedDocument> {

    final class MappedDocument {
        private final Document document;
        private final Function<Map<String, Object>, Map<String, Object>> resultMapper;

        MappedDocument(Document document, Function<Map<String, Object>, Map<String, Object>> resultMapper) {
            this.document = requireNonNull(document);
            this.resultMapper = requireNonNull(resultMapper);
        }

        public Document getDocument() {
            return document;
        }

        public Function<Map<String, Object>, Map<String, Object>> getResultMapper() {
            return resultMapper;
        }
    }
}
