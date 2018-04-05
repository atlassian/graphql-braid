package com.atlassian.braid.document;

import com.atlassian.braid.document.MappingContexts.OperationDefinitionMappingContext;
import com.atlassian.braid.document.MappingContexts.RootMappingContext;
import com.atlassian.braid.document.SelectionOperation.OperationResult;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.atlassian.braid.document.MappedDefinitions.toMappedDefinitions;
import static com.atlassian.braid.document.QueryDocuments.groupRootDefinitionsByType;
import static com.atlassian.braid.document.RootDefinitionMappingResult.toOperationMappingResult;
import static com.atlassian.braid.document.SelectionOperation.result;
import static com.atlassian.braid.document.TypeMappers.maybeFindTypeMapper;
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
        return apply(MappingContexts.rootContext(schema, typeMappers), input);
    }

    private MappedDocument apply(RootMappingContext context, Document input) {

        final Map<Class<?>, List<Definition>> rootDefinitions = groupRootDefinitionsByType(input);


        final List<FragmentDefinition> originalFragmentDefinitions = BraidObjects.cast(rootDefinitions.getOrDefault(FragmentDefinition.class, emptyList()));

        final List<MappingContext.FragmentMapping> fragmentDefinitions = originalFragmentDefinitions
                .stream()
                .map(FragmentDefinition.class::cast)
                .map(fd -> {

                    final MappingContexts.FragmentDefinitionMappingContext mappingContext = context.forFragment(fd);

                    final MappingContext.FragmentMapping o = maybeFindTypeMapper(typeMappers, mappingContext.getObjectTypeDefinition())
                            .map(tm -> tm.apply(mappingContext, fd.getSelectionSet()))
                            // TODO just below check that the selection set is not empty, if it is we can skip fragment references
                            .map(ssmr -> {

                                final FragmentDefinition fragmentDefinition = new FragmentDefinition(fd.getName(), fd.getTypeCondition(), fd.getDirectives(), ssmr.selectionSet);
                                return new MappingContext.FragmentMapping(fragmentDefinition, ssmr.resultMapper);
                            }).orElse(new MappingContext.FragmentMapping(fd, null));

                    return o;
                })
                .collect(toList());

        // TODO here we need to keep the mapping info about processed fragments

        RootMappingContext contextWithFragments = context.withFragments(originalFragmentDefinitions);

        final Stream<RootDefinitionMappingResult> rootDefinitionMappingResultStream = rootDefinitions.getOrDefault(OperationDefinition.class, emptyList())
                .stream()
                .map(OperationDefinition.class::cast)
                .map(od -> mapOperation(contextWithFragments.forOperationDefinition(od), od));
        final MappedDefinitions operationDefinitions = rootDefinitionMappingResultStream
                .collect(toMappedDefinitions());

        return new MappedDocument(
                getDocument(concat(operationDefinitions.getDefinitions(), fragmentDefinitions.stream().map(fm -> fm.fragmentDefinition).collect(toList()))),
                mapper(composed(operationDefinitions.getMappers())));
    }

    private static Document getDocument(List<? extends Definition> rootDefinitions) {
        final Document document = new Document();
        document.getDefinitions().addAll(rootDefinitions);
        return document;
    }

    private RootDefinitionMappingResult mapOperation(OperationDefinitionMappingContext mappingContext, OperationDefinition operationDefinition) {
        final Map<Boolean, List<Selection>> fieldsAndNonFields = getFieldsAndNonFields(operationDefinition);

        final List<Selection> nonFields = fieldsAndNonFields.getOrDefault(false, emptyList());
        final List<Field> fields = cast(fieldsAndNonFields.getOrDefault(true, emptyList()));

        return fields.stream()
                // TODO change the mapping context here
                .map(field -> MappingContext.forField(mappingContext.schema, mappingContext.typeMappers, mappingContext.fragmentMappings, mappingContext.getObjectTypeDefinition(), field))
                .map(TypedDocumentMapper::mapNode)
                .collect(toOperationMappingResult(operationDefinition, nonFields));
    }

    static OperationResult mapNode(MappingContext mappingContext) {
        final ObjectTypeDefinition definition = mappingContext.getObjectTypeDefinition();
        final Field field = mappingContext.getField();

        return maybeFindTypeMapper(mappingContext.getTypeMappers(), definition)
                .map(typeMapper -> typeMapper.apply(mappingContext, field.getSelectionSet()))
                .map(mappingResult -> mappingResult.toFieldOperationResult(mappingContext))
                .orElse(result(field));
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
