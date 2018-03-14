package com.atlassian.braid.source;

import com.atlassian.braid.BatchLoaderFactory;
import com.atlassian.braid.BraidContext;
import com.atlassian.braid.GraphQLQueryVisitor;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.ExecutionInput;
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
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.BatchLoaderUtils.getTargetIdFromEnvironment;
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

/**
 * Executes a query against the data source
 */
class QueryExecutor<C extends BraidContext> implements BatchLoaderFactory<C> {

    private final QueryFunction<C> queryFunction;

    QueryExecutor(QueryFunction<C> queryFunction) {
        this.queryFunction = requireNonNull(queryFunction);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return new QueryExecutorBatchLoader(schemaSource, link, queryFunction);
    }

    private static class QueryExecutorBatchLoader implements BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> {

        private final SchemaSource<?> schemaSource;

        private final Link link; // nullable

        private final QueryFunction<?> queryFunction;

        private QueryExecutorBatchLoader(SchemaSource<?> schemaSource, Link link, QueryFunction<?> queryFunction) {
            this.schemaSource = requireNonNull(schemaSource);
            this.link = link; // may be null
            this.queryFunction = requireNonNull(queryFunction);
        }

        @Override
        public CompletionStage<List<DataFetcherResult<Map<String, Object>>>> load(List<DataFetchingEnvironment> environments) {
            final DataFetchingEnvironment firstEnv = environments.stream().findFirst()
                    .orElseThrow(IllegalStateException::new);

            final GraphQLOutputType fieldType = firstEnv.getFieldDefinition().getType();

            Document doc = new Document();

            final OperationDefinition.Operation operationType = getOperationType(firstEnv).orElse(QUERY);
            OperationDefinition queryOp = newQueryOperationDefinition(fieldType, operationType);

            doc.getDefinitions().add(queryOp);

            Map<String, Object> variables = new HashMap<>();
            Map<DataFetchingEnvironment, Field> clonedFields = new HashMap<>();

            // start at 99 so that we can find variables already counter-namespaced via startsWith()
            int counter = 99;

            // this is to gather data we don't need to fetch through batch loaders, e.g. when on the the variable used in
            // the query is fetched
            final Map<String, Object> shortCircuitedData = new HashMap<>();

            // build batch queryResult
            for (DataFetchingEnvironment environment : environments) {
                ++counter;

                final Field field = cloneFieldBeingFetchedWithAlias(environment, createFieldAlias(counter));

                clonedFields.put(environment, field);

                trimFieldSelection(schemaSource, environment, field);

                // add variable and argument for linked field identifier
                if (link != null) {
                    final String targetId = getTargetIdFromEnvironment(link, environment);

                    if (isTargetIdNullAndCannotQueryLinkWithNull(targetId, link)) {
                        continue;
                    }

                    if (isFieldQueryOnlySelectingVariable(field, link)) {
                        shortCircuitedData.put(field.getAlias(), new HashMap<String, Object>() {{
                            put(link.getTargetVariableQueryField(), targetId);
                        }});
                        continue;
                    }

                    final String variableName = link.getArgumentName() + counter;

                    field.setName(link.getTargetQueryField());
                    field.setArguments(linkQueryArgumentAsList(link, variableName));

                    queryOp.getVariableDefinitions().add(linkQueryVariableDefinition(link, variableName, schemaSource));
                    variables.put(variableName, targetId);
                }

                Document queryDoc = new Parser().parseDocument(environment.<BraidContext>getContext().getQuery());
                OperationDefinition operationDefinition = findSingleOperationDefinition(queryDoc);
                final GraphQLQueryVisitor variableNameSpacer =
                        new VariableNamespacingGraphQLQueryVisitor(counter, operationDefinition, variables, environment, queryOp);

                processForFragments(environment, field).forEach(d -> {
                    variableNameSpacer.visit(d);
                    doc.getDefinitions().add(d);
                });

                variableNameSpacer.visit(field);
                queryOp.getSelectionSet().getSelections().add(field);
            }

            CompletableFuture<DataFetcherResult<Map<String, Object>>> queryResult;
            if (queryOp.getSelectionSet().getSelections().isEmpty()) {
                queryResult = CompletableFuture.completedFuture(new DataFetcherResult<>(emptyMap(), emptyList()));
            } else {
                ExecutionInput input = executeBatchQuery(doc, queryOp.getName(), variables);
                queryResult = queryFunction
                        .query(input, environments.get(0).getContext());
            }
            return queryResult
                    .thenApply(result -> {
                        final HashMap<String, Object> data = new HashMap<>();
                        data.putAll(result.getData());
                        data.putAll(shortCircuitedData);
                        return new DataFetcherResult<Map<String, Object>>(data, result.getErrors());
                    })
                    .thenApply(result -> transformBatchResultIntoResultList(environments, clonedFields, result));
        }
    }

    private static boolean isTargetIdNullAndCannotQueryLinkWithNull(String targetId, Link link) {
        return targetId == null && !link.isNullable();
    }

    private static boolean isFieldQueryOnlySelectingVariable(Field field, Link link) {
        final List<Selection> selections = field.getSelectionSet().getSelections();
        return selections.stream().allMatch(s -> s instanceof Field) &&// this means that any fragment will make this return false
                selections.stream()
                        .map(BraidObjects::<Field>cast)
                        .allMatch(f -> f.getName().equals(link.getTargetVariableQueryField()));
    }

    private static VariableDefinition linkQueryVariableDefinition(Link link, String variableName, SchemaSource<?> schemaSource) {
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
        return "Bulk_" + fieldType.getName();
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

    private static List<DataFetcherResult<Map<String, Object>>> transformBatchResultIntoResultList(List<DataFetchingEnvironment> environments, Map<DataFetchingEnvironment, Field> clonedFields, DataFetcherResult<Map<String, Object>> result) {
        List<DataFetcherResult<Map<String, Object>>> queryResults = new ArrayList<>();
        Map<String, Object> data = result.getData();
        for (DataFetchingEnvironment environment : environments) {
            Field field = clonedFields.get(environment);
            Map<String, Object> fieldData = BraidObjects.cast(data.getOrDefault(field.getAlias(), null));

            queryResults.add(new DataFetcherResult<>(
                    fieldData,
                    result.getErrors().stream()
                            .filter(e -> e.getPath() == null || e.getPath().isEmpty()
                                    || field.getAlias().equals(e.getPath().get(0)))
                            .map(RelativeGraphQLError::new)
                            .collect(toList())
            ));
        }
        return queryResults;
    }

    private static OperationDefinition findSingleOperationDefinition(Document queryDoc) {
        return queryDoc.getDefinitions().stream()
                .filter(d -> d instanceof OperationDefinition)
                .map(OperationDefinition.class::cast)
                .collect(singleton());
    }

    private static Type findArgumentType(SchemaSource<?> schemaSource, Link link) {
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
    static void trimFieldSelection(SchemaSource<?> schemaSource, DataFetchingEnvironment environment, Field field) {
        new GraphQLQueryVisitor() {
            GraphQLOutputType parentType = null;
            GraphQLOutputType lastFieldType = null;

            @Override
            protected void visitField(Field node) {
                GraphQLType type;
                if (node == field) {
                    type = environment.getFieldType();
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

            variables.put(newName, environment.<BraidContext>getContext().getVariables().get(varRef.getName()));
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
}
