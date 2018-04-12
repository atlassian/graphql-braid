package com.atlassian.braid.source;

import com.atlassian.braid.BatchLoaderFactory;
import com.atlassian.braid.BraidContext;
import com.atlassian.braid.GraphQLQueryVisitor;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.document.DocumentMapper.MappedDocument;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.language.Argument;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InputValueDefinition;
import graphql.language.Node;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.BatchLoaderUtils.getTargetIdsFromEnvironment;
import static com.atlassian.braid.TypeUtils.findQueryFieldDefinitions;
import static com.atlassian.braid.graphql.language.GraphQLNodes.printNode;
import static com.atlassian.braid.java.util.BraidCollectors.singleton;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Executes a query against the data source
 */
class QueryExecutor<C> implements BatchLoaderFactory {

    private final QueryFunction<C> queryFunction;

    QueryExecutor(QueryFunction<C> queryFunction) {
        this.queryFunction = requireNonNull(queryFunction);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource, Link link) {
        return new QueryExecutorBatchLoader<>(BraidObjects.cast(schemaSource), link, queryFunction);
    }

    private static class QueryExecutorBatchLoader<C> implements BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> {

        private final QueryExecutorSchemaSource schemaSource;

        private final Link link; // nullable

        private final QueryFunction<C> queryFunction;

        private QueryExecutorBatchLoader(QueryExecutorSchemaSource schemaSource, Link link, QueryFunction<C> queryFunction) {
            this.schemaSource = requireNonNull(schemaSource);
            this.link = link;  // may be null
            this.queryFunction = requireNonNull(queryFunction);
        }

        @Override
        public CompletionStage<List<DataFetcherResult<Object>>> load(List<DataFetchingEnvironment> environments) {

            final DataFetchingEnvironment firstEnv = environments.stream().findFirst()
                    .orElseThrow(IllegalStateException::new);

            final GraphQLOutputType fieldType = firstEnv.getFieldDefinition().getType();

            Document doc = new Document();

            final OperationDefinition.Operation operationType = getOperationType(firstEnv).orElse(QUERY);
            OperationDefinition queryOp = newQueryOperationDefinition(fieldType, operationType);

            doc.getDefinitions().add(queryOp);

            Map<String, Object> variables = new HashMap<>();
            Map<DataFetchingEnvironment, List<FieldKey>> clonedFields = new HashMap<>();

            // start at 99 so that we can find variables already counter-namespaced via startsWith()
            AtomicInteger counter = new AtomicInteger(99);

            // this is to gather data we don't need to fetch through batch loaders, e.g. when on the the variable used in
            // the query is fetched
            final Map<FieldKey, Object> shortCircuitedData = new HashMap<>();

            // build batch queryResult
            for (DataFetchingEnvironment environment : environments) {
                List<FieldRequest> fields = new ArrayList<>();
                List<Integer> usedCounterIds = new ArrayList<>();

                Document queryDoc = new Parser().parseDocument(environment.<BraidContext>getContext().getExecutionContext().getQuery());
                OperationDefinition operationDefinition = findSingleOperationDefinition(queryDoc);

                // add variable and argument for linked field identifier
                if (link != null) {
                    final List targetIds = getTargetIdsFromEnvironment(link, environment);

                    boolean fieldQueryOnlySelectingVariable = isFieldQueryOnlySelectingVariable(cloneFieldBeingFetched(environment), link);
                    for (Object targetId : targetIds) {
                        final FieldRequest field = cloneField(schemaSource, counter, usedCounterIds, environment);
                        if (isTargetIdNullAndCannotQueryLinkWithNull(targetId, link)) {
                            shortCircuitedData.put(new FieldKey(field.field.getAlias()), null);
                        } else if (fieldQueryOnlySelectingVariable) {
                            shortCircuitedData.put(new FieldKey(field.field.getAlias()), new HashMap<String, Object>() {{
                                put(link.getTargetVariableQueryField(), targetId);
                            }});
                        } else {
                            addQueryVariable(queryOp, variables, counter, targetId, field);
                            addFieldToQuery(doc, queryOp, variables, environment, operationDefinition, field);
                        }

                        fields.add(field);
                    }
                } else {
                    FieldRequest field = cloneField(schemaSource, counter, usedCounterIds, environment);
                    fields.add(field);
                    addFieldToQuery(doc, queryOp, variables, environment, operationDefinition, field);
                }
                clonedFields.put(environment, fields.stream()
                        .map(f -> f.field.getAlias())
                        .map(FieldKey::new)
                        .collect(toList()));
            }

            final MappedDocument mappedDocument = schemaSource.getDocumentMapper().apply(doc);

            CompletableFuture<DataFetcherResult<Map<String, Object>>> queryResult = executeQuery(environments, mappedDocument.getDocument(), queryOp, variables);
            return queryResult
                    .thenApply(result -> {
                        final HashMap<FieldKey, Object> data = new HashMap<>();
                        Map<FieldKey, Object> dataByKey = result.getData().entrySet().stream()
                                .collect(toMap(
                                        e -> new FieldKey(e.getKey()),
                                        Map.Entry::getValue));
                        data.putAll(dataByKey);
                        data.putAll(shortCircuitedData);
                        return new DataFetcherResult<Map<FieldKey, Object>>(data, result.getErrors());
                    })
                    .thenApply(result -> {
                        final Function<Map<String, Object>, Map<String, Object>> mapper = mappedDocument.getResultMapper();
                        final Map<String, Object> data = new HashMap<>();
                        result.getData().forEach((key, value) -> data.put(key.value, value));

                        final Map<String, Object> newData = mapper.apply(data);

                        final Map<FieldKey, Object> resultData = new HashMap<>();
                        newData.forEach((key, value) -> resultData.put(new FieldKey(key), value));
                        return new DataFetcherResult<>(resultData, result.getErrors());
                    })
                    .thenApply(result -> transformBatchResultIntoResultList(environments, clonedFields, result));
        }

        private void addFieldToQuery(Document doc, OperationDefinition queryOp, Map<String, Object> variables, DataFetchingEnvironment environment, OperationDefinition operationDefinition, FieldRequest field) {
            final GraphQLQueryVisitor variableNameSpacer =
                    new VariableNamespacingGraphQLQueryVisitor(field.counter, operationDefinition, variables, environment, queryOp);
            processForFragments(environment, field.field).forEach(d -> {
                variableNameSpacer.visit(d);
                doc.getDefinitions().add(d);
            });

            variableNameSpacer.visit(field.field);
            queryOp.getSelectionSet().getSelections().add(field.field);
        }

        private CompletableFuture<DataFetcherResult<Map<String, Object>>> executeQuery(List<DataFetchingEnvironment> environments, Document doc, OperationDefinition queryOp, Map<String, Object> variables) {

            CompletableFuture<DataFetcherResult<Map<String, Object>>> queryResult;
            if (queryOp.getSelectionSet().getSelections().isEmpty()) {
                queryResult = CompletableFuture.completedFuture(new DataFetcherResult<>(emptyMap(), emptyList()));
            } else {
                ExecutionInput input = executeBatchQuery(doc, queryOp.getName(), variables);
                final C context = BraidObjects.<BraidContext<C>>cast(environments.get(0).getContext()).getContext();
                queryResult = queryFunction
                        .query(input, context);
            }
            return queryResult;
        }

        private void addQueryVariable(OperationDefinition queryOp, Map<String, Object> variables, AtomicInteger counter, Object targetId, FieldRequest field) {
            final String variableName = link.getArgumentName() + counter;

            field.field.setName(link.getTargetQueryField());
            field.field.setArguments(linkQueryArgumentAsList(link, variableName));

            queryOp.getVariableDefinitions().add(linkQueryVariableDefinition(link, variableName, schemaSource));
            variables.put(variableName, targetId);
        }

        private FieldRequest cloneField(SchemaSource schemaSource, AtomicInteger counter, List<Integer> usedCounterIds,
                                        DataFetchingEnvironment environment) {
            final Field field = cloneFieldBeingFetchedWithAlias(environment, createFieldAlias(counter.incrementAndGet()));
            usedCounterIds.add(counter.get());
            trimFieldSelection(schemaSource, environment, field);
            return new FieldRequest(field, counter.get());
        }
    }

    private static boolean isTargetIdNullAndCannotQueryLinkWithNull(Object targetId, Link link) {
        return targetId == null && !link.isNullable();
    }

    private static boolean isFieldQueryOnlySelectingVariable(Field field, Link link) {
        final List<Selection> selections = field.getSelectionSet().getSelections();
        return selections.stream().allMatch(s -> s instanceof Field) &&// this means that any fragment will make this return false
                selections.stream()
                        .map(BraidObjects::<Field>cast)
                        .allMatch(f -> f.getName().equals(link.getTargetVariableQueryField()));
    }

    private static VariableDefinition linkQueryVariableDefinition(Link link, String variableName, SchemaSource schemaSource) {
        return new VariableDefinition(variableName, findArgumentType(schemaSource, link));
    }

    private static List<Argument> linkQueryArgumentAsList(Link link, String variableName) {
        return singletonList(new Argument(link.getArgumentName(), new VariableReference(variableName)));
    }

    private static Function<Field, String> createFieldAlias(int counter) {
        return field -> field.getName() + counter;
    }

    private static OperationDefinition newQueryOperationDefinition(GraphQLOutputType fieldType,
                                                                   OperationDefinition.Operation operationType) {
        return new OperationDefinition(newBulkOperationName(fieldType), operationType, new SelectionSet());
    }

    private static Optional<OperationDefinition.Operation> getOperationType(DataFetchingEnvironment env) {
        final GraphQLType graphQLType = env.getParentType();
        final GraphQLSchema graphQLSchema = env.getGraphQLSchema();
        if (Objects.equals(graphQLSchema.getQueryType(), graphQLType)) {
            return Optional.of(QUERY);
        } else if (Objects.equals(graphQLSchema.getMutationType(), graphQLType)) {
            return Optional.of(MUTATION);
        } else {
            return Optional.empty();
        }
    }

    private static String newBulkOperationName(GraphQLOutputType fieldType) {
        String type;
        if (fieldType instanceof GraphQLList) {
            type = ((GraphQLList) fieldType).getWrappedType().getName();
        } else {
            type = fieldType.getName();
        }
        return "Bulk_" + type;
    }

    private static Field cloneFieldBeingFetchedWithAlias(DataFetchingEnvironment environment, Function<Field, String> alias) {
        final Field field = cloneFieldBeingFetched(environment);
        field.setAlias(alias.apply(field));
        return field;
    }

    private static Field cloneFieldBeingFetched(DataFetchingEnvironment environment) {
        return DocumentCloners.clone(findCurrentFieldBeingFetched(environment));
    }

    private static ExecutionInput executeBatchQuery(Document doc, String operationName, Map<String, Object> variables) {
        return ExecutionInput.newExecutionInput()
                .query(printNode(doc))
                .operationName(operationName)
                .variables(variables)
                .build();
    }

    private static List<DataFetcherResult<Object>> transformBatchResultIntoResultList(
            List<DataFetchingEnvironment> environments,
            Map<DataFetchingEnvironment, List<FieldKey>> clonedFields,
            DataFetcherResult<Map<FieldKey, Object>> result) {
        List<DataFetcherResult<Object>> queryResults = new ArrayList<>();
        Map<FieldKey, Object> data = result.getData();
        for (DataFetchingEnvironment environment : environments) {
            List<FieldKey> fields = clonedFields.get(environment);
            Object fieldData;

            if (!fields.isEmpty()) {
                FieldKey field = fields.get(0);
                fieldData = BraidObjects.cast(data.getOrDefault(field, null));

                if (environment.getFieldType() instanceof GraphQLList && !(fieldData instanceof List)) {
                    fieldData = fields.stream()
                            .map(f -> BraidObjects.cast(data.getOrDefault(f, null)))
                            .collect(toList());
                } else if (fields.size() > 1) {
                    throw new IllegalStateException("Can't query for multiple fields if the target type isn't a list");
                }
                queryResults.add(new DataFetcherResult<>(
                        fieldData,
                        buildDataFetcherResultErrors(result, fields)
                ));
            } else if (environment.getSource() instanceof Map &&
                    environment.<Map<String, Object>>getSource().get(environment.getFieldDefinition().getName()) instanceof List) {
                queryResults.add(new DataFetcherResult<>(
                        emptyList(),
                        buildDataFetcherResultErrors(result, fields)
                ));
            } else {
                queryResults.add(new DataFetcherResult<>(
                        null,
                        buildDataFetcherResultErrors(result, fields)
                ));
            }
        }
        return queryResults;
    }

    private static List<GraphQLError> buildDataFetcherResultErrors(DataFetcherResult<Map<FieldKey, Object>> result, List<FieldKey> fields) {
        return result.getErrors().stream()
                .filter(e -> e.getPath() == null || e.getPath().isEmpty()
                        || fields.contains(new FieldKey(String.valueOf(e.getPath().get(0)))))
                .map(RelativeGraphQLError::new)
                .collect(toList());
    }

    private static Predicate<GraphQLError> isErrorForField(Field field) {
        return e -> e.getPath() == null
                || e.getPath().isEmpty()
                || field.getAlias().equals(e.getPath().get(0));
    }

    private static OperationDefinition findSingleOperationDefinition(Document queryDoc) {
        return queryDoc.getDefinitions().stream()
                .filter(d -> d instanceof OperationDefinition)
                .map(OperationDefinition.class::cast)
                .collect(singleton());
    }

    private static Type findArgumentType(SchemaSource schemaSource, Link link) {
        return findQueryFieldDefinitions(schemaSource.getPrivateSchema())
                .orElseThrow(IllegalStateException::new)
                .stream()
                .filter(f -> f.getName().equals(link.getTargetQueryField()))
                .findFirst()
                .map(f -> f.getInputValueDefinitions().stream()
                        .filter(iv -> iv.getName().equals(link.getArgumentName()))
                        .findFirst()
                        .map(InputValueDefinition::getType)
                        .orElseThrow(IllegalArgumentException::new))
                .orElseThrow(IllegalArgumentException::new);
    }

    private static Field findCurrentFieldBeingFetched(DataFetchingEnvironment environment) {
        return environment.getFields()
                .stream()
                .filter(isFieldMatchingFieldDefinition(environment.getFieldDefinition()))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private static Predicate<Field> isFieldMatchingFieldDefinition(GraphQLFieldDefinition fieldDefinition) {
        return field -> Objects.equals(fieldDefinition.getName(), field.getName());
    }

    /**
     * Ensures we only ask for fields the data source supports
     */
    static void trimFieldSelection(SchemaSource schemaSource, DataFetchingEnvironment environment, Field field) {
        new GraphQLQueryVisitor() {
            GraphQLOutputType parentType = null;
            GraphQLOutputType lastFieldType = null;

            @Override
            protected void visitField(Field node) {
                GraphQLType type;
                if (node == field) {
                    type = environment.getFieldType();
                    parentType = (GraphQLObjectType) environment.getParentType();
                    Optional<Link> linkWithDifferentFromField = getLinkWithDifferentFromField(schemaSource.getLinks(), parentType.getName(), field.getName());
                    if (linkWithDifferentFromField.isPresent() && environment.getSource() == null) {
                        field.setSelectionSet(null);
                        field.setName(linkWithDifferentFromField.get().getSourceFromField());
                    }
                } else {
                    getLink(schemaSource.getLinks(), parentType.getName(), node.getName())
                            .ifPresent(l -> node.setSelectionSet(null));
                    if (isFieldMatchingFieldDefinition(TypeNameMetaFieldDef).test(node)) {
                        type = TypeNameMetaFieldDef.getType();
                    } else if (parentType instanceof GraphQLInterfaceType) {
                        type = ((GraphQLInterfaceType) parentType).getFieldDefinition(node.getName()).getType();
                    } else {
                        type = ((GraphQLObjectType) parentType).getFieldDefinition(node.getName()).getType();
                    }
                }

                while (type instanceof GraphQLModifiedType) {
                    type = ((GraphQLModifiedType) type).getWrappedType();
                }
                lastFieldType = (GraphQLOutputType) type;
                super.visitField(node);
            }

            @Override
            protected void visitSelectionSet(final SelectionSet node) {
                if (node == null) {
                    return;
                }

                if (!node.getChildren().isEmpty()) {
                    GraphQLOutputType lastParentType = parentType;
                    parentType = lastFieldType;
                    for (final Node child : node.getChildren()) {

                        // process child to handle cases where the source from field is different than the source field
                        if (child instanceof Field) {
                            Optional<Link> linkWithDifferentFromField = getLinkWithDifferentFromField(schemaSource.getLinks(), parentType.getName(), ((Field) child).getName());
                            if (linkWithDifferentFromField.isPresent()) {
                                removeSourceFieldIfDifferentThanFromField(node, linkWithDifferentFromField.get());
                                addFromFieldToQueryIfMissing(node, linkWithDifferentFromField.get());
                            }
                        }
                        visit(child);
                    }
                    parentType = lastParentType;
                }
            }

            private void addFromFieldToQueryIfMissing(SelectionSet node, Link link) {
                Optional<Selection> fromField = node.getSelections().stream()
                        .filter(s -> s instanceof Field
                                && ((Field) s).getName().equals(link.getSourceFromField()))
                        .findFirst();
                if (!fromField.isPresent()) {
                    node.getSelections().add(new Field(link.getSourceFromField()));
                }
            }

            private void removeSourceFieldIfDifferentThanFromField(SelectionSet node, Link link) {
                node.getSelections().stream()
                        .filter(s -> s instanceof Field
                                && ((Field) s).getName().equals(link.getSourceField()))
                        .findAny()
                        .ifPresent(s -> node.getSelections().remove(s));
            }
        }.visit(field);
    }

    /**
     * Ensures any referenced fragments are included in the query
     */
    static Collection<Definition> processForFragments(DataFetchingEnvironment environment, Field field) {
        Map<String, Definition> result = new HashMap<>();
        new GraphQLQueryVisitor() {
            @Override
            protected void visitFragmentSpread(FragmentSpread node) {
                FragmentDefinition fragmentDefinition = environment.getFragmentsByName().get(node.getName());
                result.put(node.getName(), fragmentDefinition.deepCopy());
                super.visitFragmentSpread(node);
            }
        }.visit(field);
        return result.values();
    }

    private static Optional<Link> getLink(Collection<Link> links, String typeName, String fieldName) {
        return links.stream()
                .filter(l -> l.getSourceType().equals(typeName) && l.getSourceFromField().equals(fieldName))
                .findFirst();
    }

    private static Optional<Link> getLinkWithDifferentFromField(Collection<Link> links, String typeName, String fieldName) {
        return links.stream()
                .filter(l -> l.getSourceType().equals(typeName)
                        && l.getSourceField().equals(fieldName)
                        && !l.getSourceFromField().equals(fieldName))
                .findFirst();
    }


    private static class VariableNamespacingGraphQLQueryVisitor extends GraphQLQueryVisitor {
        private final int counter;
        private final OperationDefinition queryType;
        private final Map<String, Object> variables;
        private final DataFetchingEnvironment environment;
        private final OperationDefinition queryOp;

        VariableNamespacingGraphQLQueryVisitor(int counter,
                                               OperationDefinition operationDefinition,
                                               Map<String, Object> variables,
                                               DataFetchingEnvironment environment,
                                               OperationDefinition queryOp) {
            this.counter = counter;
            this.queryType = operationDefinition;
            this.variables = variables;
            this.environment = environment;
            this.queryOp = queryOp;
        }

        @Override
        protected void visitField(Field node) {
            node.setArguments(node.getArguments().stream().map(this::namespaceReferences).collect(toList()));
            super.visitField(node);
        }

        private Argument namespaceReferences(Argument arg) {
            return new Argument(arg.getName(), namespaceReferences(arg.getValue()));
        }

        private Value namespaceReferences(Value value) {
            final Value transformedValue;
            if (value instanceof VariableReference) {
                transformedValue = maybeNamespaceReference((VariableReference) value);
            } else if (value instanceof ObjectValue) {
                transformedValue = namespaceReferencesForObjectValue((ObjectValue) value);
            } else {
                transformedValue = value;
            }
            return transformedValue;
        }

        private ObjectValue namespaceReferencesForObjectValue(ObjectValue value) {
            return new ObjectValue(
                    value.getChildren().stream()
                            .map(ObjectField.class::cast)
                            .map(o -> new ObjectField(o.getName(), namespaceReferences(o.getValue())))
                            .collect(toList()));
        }

        private VariableReference maybeNamespaceReference(VariableReference value) {
            return isVariableAlreadyNamespaced(value) ? value : namespaceVariable(value);
        }

        private VariableReference namespaceVariable(VariableReference varRef) {
            final String newName = varRef.getName() + counter;

            final VariableReference value = new VariableReference(newName);
            final Type type = findVariableType(varRef, queryType);

            variables.put(newName, environment.<BraidContext>getContext().getExecutionContext().getVariables().get(varRef.getName()));
            queryOp.getVariableDefinitions().add(new VariableDefinition(newName, type));
            return value;
        }

        private boolean isVariableAlreadyNamespaced(VariableReference varRef) {
            return varRef.getName().endsWith(String.valueOf(counter));
        }

        private static Type findVariableType(VariableReference varRef, OperationDefinition queryType) {
            return queryType.getVariableDefinitions()
                    .stream()
                    .filter(d -> d.getName().equals(varRef.getName()))
                    .map(VariableDefinition::getType)
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new);
        }
    }

    private static class FieldRequest {
        private final Field field;
        private final int counter;

        private FieldRequest(Field field, int counter) {
            this.field = field;
            this.counter = counter;
        }
    }

    // The unique key of a id that is being requested
    private static class FieldKey {
        private final String value;

        private FieldKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldKey fieldKey = (FieldKey) o;
            return Objects.equals(value, fieldKey.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
