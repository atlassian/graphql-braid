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
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.atlassian.braid.TypeUtils.findQueryType;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static java.util.Collections.singletonList;

/**
 * Executes a query against the data source
 */
class QueryExecutor {

    <C extends BraidContext> BatchLoader<DataFetchingEnvironment, Object> asBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return environments -> query(schemaSource, environments, link);
    }
    @SuppressWarnings("Duplicates")
    <C extends BraidContext> CompletableFuture<List<Object>> query(SchemaSource<C> schemaSource, List<DataFetchingEnvironment> environments, Link link) {
        Document doc = new Document();
        OperationDefinition queryOp = new OperationDefinition(
                "",
                OperationDefinition.Operation.QUERY, new SelectionSet());
        queryOp.setName("Bulk_" + environments.stream()
                .findFirst()
                .map(e -> e.getFieldDefinition().getType().getName())
                .orElse(""));
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
                Map<String, String> source = findMapSource(environment);
                Type argumentType = findArgumentType(schemaSource, link);
                //noinspection unchecked
                variables.put(varName, source.get(link.getSourceFromField()));
                queryOp.getVariableDefinitions().add(new VariableDefinition(varName, argumentType));
            }

            Document queryDoc = new Parser().parseDocument(environment.<BraidContext>getContext().getQuery());
            OperationDefinition queryType = findQueryDefinition(queryDoc);

            processForFragments(environment, field).forEach(d -> doc.getDefinitions().add(d));
            queryOp.getSelectionSet().getSelections().add(field);

            int finalCounter = counter;
            new GraphQLQueryVisitor() {
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
                            }).collect(Collectors.toList());
                    node.setArguments(renamedArguments);
                    super.visitField(node);
                }

                private Value namespaceVariable(VariableReference varRef) {
                    Value value;
                    value = new VariableReference(varRef.getName() + finalCounter);
                    Type type = findVariableType(varRef, queryType);
                    variables.put(varRef.getName() + finalCounter, environment.<BraidContext>getContext().getVariables().get(varRef.getName()));
                    queryOp.getVariableDefinitions().add(new VariableDefinition(varRef.getName() + finalCounter, type));
                    return value;
                }

                private boolean isVariableNotAlreadyNamespaced(VariableReference varRef) {
                    return !varRef.getName().endsWith(String.valueOf(finalCounter));
                }
            }.visit(doc);
        }

        ExecutionInput input = executeBatchQuery(doc, queryOp.getName(), variables);

        return schemaSource.query(input, environments.get(0).getContext())
                .thenApply(result -> transformBatchResultIntoResultList(environments, clonedFields, result)
                );
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

    private Type findVariableType(VariableReference varRef, OperationDefinition queryType) {
        return queryType.getVariableDefinitions().stream().filter(d ->
            d.getName().equals(varRef.getName()))
                .map(VariableDefinition::getType)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private List<Object> transformBatchResultIntoResultList(List<DataFetchingEnvironment> environments, Map<DataFetchingEnvironment, Field> clonedFields, DataFetcherResult<Map<String, Object>> result) {
        List<Object> queryResults = new ArrayList<>();
        Map<String, Object> data = result.getData();
        for (DataFetchingEnvironment environment : environments) {
            Field field = clonedFields.get(environment);
            Object fieldData = data.getOrDefault(field.getAlias(), null);

            queryResults.add(new DataFetcherResult<>(
                    fieldData,
                    result.getErrors().stream()
                        .filter(e -> e.getPath() == null || e.getPath().isEmpty()
                                || field.getAlias().equals(e.getPath().get(0)))
                        .map(RelativeGraphQLError::new)
                        .collect(Collectors.toList())
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
        return findQueryType(schemaSource.getSchema()).getFieldDefinitions().stream()
                .filter(f -> f.getName().equals(link.getTargetField()))
                .findFirst()
                .map(f -> f.getInputValueDefinitions().stream()
                        .filter(iv -> iv.getName().equals(link.getArgumentName()))
                        .findFirst()
                        .map(InputValueDefinition::getType)
                        .orElseThrow(IllegalArgumentException::new))
                .orElseThrow(IllegalArgumentException::new);
    }

    private Map<String, String> findMapSource(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        while (!(source instanceof Map)) {
            if (source instanceof CompletableFuture) {
                try {
                    source = ((CompletableFuture) source).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            } else if (source instanceof DataFetcherResult) {
                source = ((DataFetcherResult) source).getData();
            } else {
                throw new IllegalArgumentException("Unexpected parent type");
            }
        }
        //noinspection unchecked
        return (Map<String, String>) source;
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
                    type = ((GraphQLModifiedType)type).getWrappedType();
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
                                && ((Field)s).getName().equals(link.getSourceField()))
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
}
