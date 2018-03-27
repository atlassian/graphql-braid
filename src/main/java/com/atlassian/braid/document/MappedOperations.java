package com.atlassian.braid.document;

import com.atlassian.braid.document.DocumentMapper.MappedDocument;
import com.atlassian.braid.mapper.Mapper;
import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Document;
import graphql.language.OperationDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

import static com.atlassian.braid.mapper.MapperOperations.composed;
import static com.atlassian.braid.mapper.Mappers.mapper;

/**
 * <p>This is an intermediary <strong>internal</strong> <em>mutable</em> class used to collect GraphQL operations that
 * have been mapped.
 * <p>It collects both the operation definitions and the {@link MapperOperation mapper operations} used to process the
 * queried data
 * <p>The main entry point of this class is its {@link #toMappedDocument() collector} that allows collecting
 * {@link OperationMappingResult}s into a full blown GraphQL {@link Document}
 */
final class MappedOperations {
    private final List<OperationDefinition> definitions;
    private final List<MapperOperation> mappers;

    private MappedOperations() {
        this.definitions = new ArrayList<>();
        this.mappers = new ArrayList<>();
    }

    private void add(OperationMappingResult result) {
        definitions.add(result.toOperationDefinition());
        mappers.addAll(result.getMapperOperations());
    }

    private static MappedOperations combine(MappedOperations mos1, MappedOperations mos2) {
        final MappedOperations mappedOperations = new MappedOperations();
        mappedOperations.definitions.addAll(mos1.definitions);
        mappedOperations.definitions.addAll(mos2.definitions);

        mappedOperations.mappers.addAll(mos1.mappers);
        mappedOperations.mappers.addAll(mos2.mappers);

        return mappedOperations;
    }

    private MappedDocument getMappedDocument() {
        return new MappedDocument(getDocument(), getMapper());
    }

    private Document getDocument() {
        final Document document = new Document();
        document.getDefinitions().addAll(definitions);
        return document;
    }

    private Mapper getMapper() {
        return mapper(composed(mappers));
    }

    /**
     * Returns a collector used to collect {@link OperationMappingResult}s into a GraphQL {@link Document}
     *
     * @return a {@link Collector} of  {@link OperationMappingResult} to {@link MappedDocument}
     */
    static Collector<OperationMappingResult, MappedOperations, MappedDocument> toMappedDocument() {
        return Collector.of(
                MappedOperations::new,
                MappedOperations::add,
                MappedOperations::combine,
                MappedOperations::getMappedDocument);
    }
}
