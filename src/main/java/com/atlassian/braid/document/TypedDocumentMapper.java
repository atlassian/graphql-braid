package com.atlassian.braid.document;

import com.atlassian.braid.document.MappingContext.FragmentDefinitionMappingContext;
import com.atlassian.braid.document.MappingContext.OperationDefinitionMappingContext;
import com.atlassian.braid.document.MappingContext.RootMappingContext;
import com.atlassian.braid.document.SelectionOperation.OperationResult;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Map;

import static com.atlassian.braid.document.MappedDefinitions.toMappedDefinitions;
import static com.atlassian.braid.document.MappingContext.rootContext;
import static com.atlassian.braid.document.QueryDocuments.groupRootDefinitionsByType;
import static com.atlassian.braid.document.RootDefinitionMappingResult.toOperationMappingResult;
import static com.atlassian.braid.document.SelectionOperation.result;
import static com.atlassian.braid.java.util.BraidLists.concat;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.mapper.MapperOperations.composed;
import static com.atlassian.braid.mapper.Mappers.mapper;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * <strong>Internal</strong> implementation of the {@link DocumentMapper} that maps based on types
 * using {@link TypeMapper type mappers}
 *
 * @see TypeMapper
 */
final class TypedDocumentMapper implements DocumentMapper {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;

    TypedDocumentMapper(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
    }

    @Override
    public MappedDocument apply(Document input) {
        return apply(rootContext(schema, typeMappers), input);
    }

    private MappedDocument apply(RootMappingContext context, Document input) {
        return apply(context, groupRootDefinitionsByType(input));
    }

    private MappedDocument apply(RootMappingContext context, Map<Class<?>, List<Definition>> rootDefinitions) {

        final List<FragmentDefinition> fragmentDefinitions =
                getDefinitionsOfType(rootDefinitions, FragmentDefinition.class);

        final List<OperationDefinition> operationDefinitions =
                getDefinitionsOfType(rootDefinitions, OperationDefinition.class);

        // keep the original fragment definitions for building mappers
        // see com.atlassian.braid.document.FragmentSpreadOperation#applyToType
        final RootMappingContext contextWithFragmentDefinitions = context.withFragments(fragmentDefinitions);

        return apply(contextWithFragmentDefinitions, operationDefinitions, fragmentDefinitions);
    }

    private MappedDocument apply(RootMappingContext context,
                                 List<OperationDefinition> operationDefinitions,
                                 List<FragmentDefinition> fragmentDefinitions) {
        final List<? extends Definition> mappedFragmentDefinitions = mapFragmentDefinitions(context, fragmentDefinitions);

        final MappedDefinitions mappedOperationDefinitions = mapOperationDefinitions(
                context,
                operationDefinitions);

        return new MappedDocument(
                getDocument(concat(mappedOperationDefinitions.getDefinitions(), mappedFragmentDefinitions)),
                mapper(composed(mappedOperationDefinitions.getMappers())));
    }

    private List<FragmentDefinition> mapFragmentDefinitions(RootMappingContext context,
                                                            List<FragmentDefinition> fragmentDefinitions) {
        return fragmentDefinitions
                .stream()
                .map(fd -> mapFragment(context.forFragment(fd), fd))
                .collect(toList());
    }

    private static FragmentDefinition mapFragment(FragmentDefinitionMappingContext mappingContext, FragmentDefinition fd) {
        return mappingContext.getTypeMapper()
                .map(tm -> tm.apply(mappingContext, fd.getSelectionSet()))
                // TODO just below check that the selection set is not empty, if it is we can skip fragment references
                .map(SelectionSetMappingResult::getSelectionSet)
                .map(ss -> new FragmentDefinition(fd.getName(), fd.getTypeCondition(), fd.getDirectives(), ss))
                .orElse(fd);
    }

    private static MappedDefinitions mapOperationDefinitions(RootMappingContext context,
                                                             List<OperationDefinition> operationDefinitions) {
        return operationDefinitions
                .stream()
                .map(od -> mapOperation(context.forOperationDefinition(od), od))
                .collect(toMappedDefinitions());
    }

    private static <D extends Definition> List<D> getDefinitionsOfType(Map<Class<?>, List<Definition>> rootDefinitions, Class<D> type) {
        return BraidObjects.cast(rootDefinitions.getOrDefault(type, emptyList()));
    }

    private static Document getDocument(List<? extends Definition> rootDefinitions) {
        final Document document = new Document();
        document.getDefinitions().addAll(rootDefinitions);
        return document;
    }

    private static RootDefinitionMappingResult mapOperation(OperationDefinitionMappingContext mappingContext, OperationDefinition operationDefinition) {
        final Map<Boolean, List<Selection>> fieldsAndNonFields = getFieldsAndNonFields(operationDefinition);

        final List<Selection> nonFields = fieldsAndNonFields.getOrDefault(false, emptyList());
        final List<Field> fields = cast(fieldsAndNonFields.getOrDefault(true, emptyList()));

        return fields.stream()
                .map(SelectionMapper::getSelectionMapper)
                .map(selectionMapper -> selectionMapper.map(mappingContext))
                .collect(toOperationMappingResult(operationDefinition, nonFields));
    }

    /**
     * Groups {@link Selection selections} by type, the key {@code true} for those of type {@link Field}, {@code false}
     * for any other type
     *
     * @param operationDefinition the operation definition for which we care about fields
     * @return a map of {@link Field fields} and <em>other</em> {@link Selection selection types}
     */
    private static Map<Boolean, List<Selection>> getFieldsAndNonFields(OperationDefinition operationDefinition) {
        return operationDefinition.getSelectionSet().getSelections()
                .stream()
                .collect(groupingBy(s -> s instanceof Field));
    }
}
