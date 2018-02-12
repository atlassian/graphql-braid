package com.atlassian.braid;

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
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.atlassian.braid.TypeUtils.findQueryFieldDefinitions;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Executes a query against the data source
 */
class QueryExecutor implements BatchLoaderFactory {

    @Override
    public <C extends BraidContext> BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return environments -> query(schemaSource, environments, link);
    }

    @SuppressWarnings("Duplicates")
    <C extends BraidContext> CompletableFuture<List<DataFetcherResult<Map<String, Object>>>> query(SchemaSource<C> schemaSource, List<DataFetchingEnvironment> environments, Link link) {
        Document doc = new Document();

        OperationDefinition queryOp = newQueryOperationDefinition(environments);

        doc.getDefinitions().add(queryOp);

        Map<String, Object> variables = new HashMap<>();
        Map<DataFetchingEnvironment, Field> clonedFields = new HashMap<>();

        // start at 99 so that we can find variables already counter-namespaced via startsWith()
        int counter = 99;
        // build batch query
        for (DataFetchingEnvironment environment : environments) {
            Field field = cloneCurrentField(environment);

            field.setAlias(field.getName() + ++counter);
            clonedFields.put(environment, field);

            trimFieldSelection(schemaSource, environment, field);

            // add variable and argument for linked field identifier
            if (link != null) {
                // set the argument from the value of the original field
                String varName = link.getArgumentName() + counter;
                field.setArguments(singletonList(
                        new Argument(link.getArgumentName(), new VariableReference(varName))
                ));
                field.setName(link.getTargetField());
                String targetId = BatchLoaderUtils.getTargetIdFromEnvironment(link, environment);
                Type argumentType = findArgumentType(schemaSource, link);
                variables.put(varName, targetId);
                queryOp.getVariableDefinitions().add(new VariableDefinition(varName, argumentType));
            }

            Document queryDoc = new Parser().parseDocument(environment.<BraidContext>getContext().getQuery());
            OperationDefinition queryType = findQueryDefinition(queryDoc);
            final GraphQLQueryVisitor variableNameSpacer =
                    new VariableNamespacingGraphQLQueryVisitor(counter, queryType, variables, environment, queryOp);

            processForFragments(environment, field).forEach(d -> {
                variableNameSpacer.visit(d);
                doc.getDefinitions().add(d);
            });

            variableNameSpacer.visit(field);
            queryOp.getSelectionSet().getSelections().add(field);
        }

        ExecutionInput input = executeBatchQuery(doc, queryOp.getName(), variables);

        return schemaSource
                .query(input, environments.get(0).getContext())
                .thenApply(result -> transformBatchResultIntoResultList(environments, clonedFields, result));
    }

    private OperationDefinition newQueryOperationDefinition(List<DataFetchingEnvironment> environments) {
        return new OperationDefinition(newBulkOperationName(environments), OperationDefinition.Operation.QUERY, new SelectionSet());
    }

    private String newBulkOperationName(List<DataFetchingEnvironment> environments) {
        return "Bulk_" + environments.stream().findFirst().map(this::getFieldTypeName).orElse("");
    }

    private String getFieldTypeName(DataFetchingEnvironment environment) {
        return environment.getFieldDefinition().getType().getName();
    }

    private Field cloneCurrentField(DataFetchingEnvironment environment) {
        Field original = findFieldWithName(environment);
        return DocumentCloners.clone(original);
    }

    private ExecutionInput executeBatchQuery(Document doc, String operationName, Map<String, Object> variables) {
        GraphQLQueryPrinter printer = new GraphQLQueryPrinter();
        String query = printer.print(doc);
        return ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .variables(variables)
                .build();
    }



    private List<DataFetcherResult<Map<String, Object>>> transformBatchResultIntoResultList(List<DataFetchingEnvironment> environments, Map<DataFetchingEnvironment, Field> clonedFields, DataFetcherResult<Map<String, Object>> result) {
        List<DataFetcherResult<Map<String, Object>>> queryResults = new ArrayList<>();
        Map<String, Object> data = result.getData();
        for (DataFetchingEnvironment environment : environments) {
            Field field = clonedFields.get(environment);
            Map<String, Object> fieldData = (Map<String, Object>) data.getOrDefault(field.getAlias(), null);

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

    private OperationDefinition findQueryDefinition(Document queryDoc) {
        return (OperationDefinition) queryDoc.getDefinitions().stream()
                .filter(d -> d instanceof OperationDefinition
                        && ((OperationDefinition) d).getOperation() == OperationDefinition.Operation.QUERY)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private <C extends BraidContext> Type findArgumentType(SchemaSource<C> schemaSource, Link link) {
        return findQueryFieldDefinitions(schemaSource.getPrivateSchema())
                .orElseThrow(IllegalStateException::new)
                .stream()
                .filter(f -> f.getName().equals(link.getTargetField()))
                .findFirst()
                .map(f -> f.getInputValueDefinitions().stream()
                        .filter(iv -> iv.getName().equals(link.getArgumentName()))
                        .findFirst()
                        .map(InputValueDefinition::getType)
                        .orElseThrow(IllegalArgumentException::new))
                .orElseThrow(IllegalArgumentException::new);
    }

    private Field findFieldWithName(DataFetchingEnvironment environment) {
        return environment.getFields().stream()
                .filter(f -> environment.getFieldDefinition().getName().equals(f.getName()))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    /**
     * Ensures we only ask for fields the data source supports
     */
    <C> void trimFieldSelection(SchemaSource<C> schemaSource, DataFetchingEnvironment environment, Field field) {
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
                    if (TypeNameMetaFieldDef.getName().equals(node.getName())) {
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
    Collection<Definition> processForFragments(DataFetchingEnvironment environment, Field field) {
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

    private Optional<Link> getLink(Collection<Link> links, String typeName, String fieldName) {
        return links.stream()
                .filter(l -> l.getSourceType().equals(typeName) && l.getSourceFromField().equals(fieldName))
                .findFirst();
    }

    private Optional<Link> getLinkWithDifferentFromField(Collection<Link> links, String typeName, String fieldName) {
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
            List<Argument> renamedArguments = node.getArguments().stream()
                    .map(a -> {
                        Value value = a.getValue();
                        if (value instanceof VariableReference) {
                            VariableReference varRef = (VariableReference) value;
                            if (isVariableNotAlreadyNamespaced(varRef)) {
                                value = namespaceVariable(varRef);
                            }
                        }
                        return new Argument(a.getName(), value);
                    }).collect(toList());
            node.setArguments(renamedArguments);
            super.visitField(node);
        }

        private Value namespaceVariable(VariableReference varRef) {
            final String newName = varRef.getName() + counter;

            Value value = new VariableReference(newName);
            Type type = findVariableType(varRef, queryType);

            variables.put(newName, environment.<BraidContext>getContext().getVariables().get(varRef.getName()));
            queryOp.getVariableDefinitions().add(new VariableDefinition(newName, type));
            return value;
        }

        private boolean isVariableNotAlreadyNamespaced(VariableReference varRef) {
            return !varRef.getName().endsWith(String.valueOf(counter));
        }

        private Type findVariableType(VariableReference varRef, OperationDefinition queryType) {
            return queryType.getVariableDefinitions()
                    .stream()
                    .filter(d -> d.getName().equals(varRef.getName()))
                    .map(VariableDefinition::getType)
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new);
        }
    }
}
