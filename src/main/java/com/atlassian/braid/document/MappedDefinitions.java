package com.atlassian.braid.document;

import com.atlassian.braid.document.DocumentMapper.MappedDocument;
import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Definition;
import graphql.language.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

/**
 * <p>This is an intermediary <strong>internal</strong> <em>mutable</em> class used to collect GraphQL operations that
 * have been mapped.
 * <p>It collects both the operation definitions and the {@link MapperOperation mapper operations} used to process the
 * queried data
 * <p>The main entry point of this class is its {@link #toMappedDocument() collector} that allows collecting
 * {@link RootDefinitionMappingResult}s into a full blown GraphQL {@link Document}
 */
final class MappedDefinitions {
    private final List<Definition> definitions;
    private final List<MapperOperation> mappers;

    private MappedDefinitions() {
        this.definitions = new ArrayList<>();
        this.mappers = new ArrayList<>();
    }

    private void add(RootDefinitionMappingResult<Definition> result) {
        definitions.add(result.toDefinition());
        mappers.addAll(result.getMapperOperations());
    }

    private static MappedDefinitions combine(MappedDefinitions mos1, MappedDefinitions mos2) {
        final MappedDefinitions mappedDefinitions = new MappedDefinitions();
        mappedDefinitions.definitions.addAll(mos1.definitions);
        mappedDefinitions.definitions.addAll(mos2.definitions);

        mappedDefinitions.mappers.addAll(mos1.mappers);
        mappedDefinitions.mappers.addAll(mos2.mappers);

        return mappedDefinitions;
    }

    List<Definition> getDefinitions() {
        return definitions;
    }

    List<MapperOperation> getMappers() {
        return mappers;
    }

    /**
     * Returns a collector used to collect {@link RootDefinitionMappingResult}s into a GraphQL {@link Document}
     *
     * @return a {@link Collector} of  {@link RootDefinitionMappingResult} to {@link MappedDocument}
     */
    static Collector<RootDefinitionMappingResult, MappedDefinitions, MappedDefinitions> toMappedDefinitions() {
        return Collector.of(
                MappedDefinitions::new,
                MappedDefinitions::add,
                MappedDefinitions::combine);
    }
}
