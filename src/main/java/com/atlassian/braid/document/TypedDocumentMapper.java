package com.atlassian.braid.document;

import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import com.atlassian.braid.mapper.Mappers;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.findOperationDefinitions;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class TypedDocumentMapper implements DocumentMapper {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;

    public TypedDocumentMapper(TypeDefinitionRegistry schema) {
        this(schema, Collections.emptyList());
    }

    public TypedDocumentMapper(TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
    }

    @Override
    public MappedDocument apply(Document input) {
        final Document output = new Document();

        final MapperOperation reduce = getOperationDefinitionStream(input)
                .map(d -> {

                    final ObjectTypeDefinition operationTypeDefinition = findOperationTypeDefinition(schema, d);

                    final List<Selection> outputSelections = new ArrayList<>();

                    final Map<Boolean, List<Selection>> fieldsAndNonFields =
                            d.getSelectionSet().getSelections().stream().collect(groupingBy(s -> s instanceof Field));

                    // take care of all non-Field selection
                    outputSelections.addAll(fieldsAndNonFields.getOrDefault(false, emptyList()));


                    final List<FieldOperation.OperationResult> operationResults = BraidObjects.<List<Field>>cast(fieldsAndNonFields.getOrDefault(true, emptyList()))
                            .stream()
                            .map(s -> {
                                final Field field = BraidObjects.cast(s);
                                final ObjectTypeDefinition fieldObjectTypeDefinition =
                                        findOutputTypeForField(schema, operationTypeDefinition, field);
                                return mapField(fieldObjectTypeDefinition, field);
                            })
                            .collect(toList());

                    final MapperOperation mapper = operationResults.stream()
                            .peek(or -> or.getField().ifPresent(outputSelections::add))
                            .map(FieldOperation.OperationResult::getMapper)
                            .reduce((o1, o2) -> MapperOperations.composed(o1, o2))
                            .orElse(MapperOperations.noop());


//                    mapper().map(d.getName(), mapper)
                    final SelectionSetMapping mapped =
                            new SelectionSetMapping(new SelectionSet(outputSelections), mapper);


                    output.getDefinitions().add(newOperationDefinition(d, mapped));
                    return mapped.getResultMapper();
                })
                .reduce((o1, o2) -> MapperOperations.composed(o1, o2))
                .orElse(MapperOperations.noop());

        return new MappedDocument(output, Mappers.mapper(reduce));
    }

    private ObjectTypeDefinition findOutputTypeForField(TypeDefinitionRegistry schema,
                                                        ObjectTypeDefinition parent, Field field) {
        return parent.getFieldDefinitions().stream()
                .filter(fd -> fd.getName().equals(field.getName())).findFirst()
                .flatMap(fd -> schema.getType(fd.getType()))
                .map(ObjectTypeDefinition.class::cast)
                .orElseThrow(IllegalStateException::new);
    }

    private FieldOperation.OperationResult mapField(ObjectTypeDefinition definition, Field field) {
        final MappingContext mappingContext = MappingContext.of(field, field.getAlias() != null ? field.getAlias() : field.getName());

        return typeMappers.stream()
                .filter(tm -> tm.test(definition))
                .findFirst()
                .map(tm -> {
                    final SelectionSetMapping apply = tm.apply(mappingContext, field.getSelectionSet());
                    final Field newField = new Field(field.getName(), field.getAlias(), field.getArguments(), field.getDirectives(), apply.getSelectionSet());

                    final MapperOperation mapper = MapperOperations.map(
                            field.getAlias() != null ? field.getAlias() : field.getName(),
                            Mappers.mapper(apply.getResultMapper()));
//                    final Mapper mapper = mapper().map(field.getAlias() != null ? field.getAlias() : field.getName(), apply.getResultMapper());

                    return FieldOperation.result(newField, mapper);
                })
                .orElse(FieldOperation.result(field));
    }

    public Stream<OperationDefinition> getOperationDefinitionStream(Document input) {
        return input.getDefinitions().stream()
                .filter(d -> d instanceof OperationDefinition)
                .map(OperationDefinition.class::cast);
    }

    private OperationDefinition newOperationDefinition(OperationDefinition original,
                                                       SelectionSetMapping selectionSetMapping) {
        return new OperationDefinition(
                original.getName(),
                original.getOperation(),
                original.getVariableDefinitions(),
                original.getDirectives(),
                selectionSetMapping.getSelectionSet());
    }

    private static ObjectTypeDefinition findOperationTypeDefinition(TypeDefinitionRegistry schema, OperationDefinition op) {
        return findOperationDefinitions(schema)
                .flatMap(maybeFindOperationTypeDefinition(op))
                .map(OperationTypeDefinition::getType)
                .flatMap(schema::getType)
                .map(BraidObjects::<ObjectTypeDefinition>cast)
                .orElseThrow(IllegalStateException::new);
    }

    private static Function<List<OperationTypeDefinition>, Optional<OperationTypeDefinition>> maybeFindOperationTypeDefinition(OperationDefinition op) {
        return ops -> ops.stream().filter(isOperationTypeDefinitionForOperationType(op)).findFirst();
    }

    private static Predicate<OperationTypeDefinition> isOperationTypeDefinitionForOperationType(OperationDefinition op) {
        return otd -> otd.getName().equalsIgnoreCase(op.getOperation().name());
    }
}
