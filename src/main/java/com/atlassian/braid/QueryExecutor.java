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
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

/**
 * Executes a query against the data source
 */
class QueryExecutor {

    <C> BatchLoader<DataFetchingEnvironment, Object> asBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return environments -> query(schemaSource, environments, link);
    }
    <C> CompletableFuture<List<Object>> query(SchemaSource<C> schemaSource, List<DataFetchingEnvironment> environments, Link link) {
        Document doc = new Document();
        OperationDefinition queryOp = new OperationDefinition(
                "",
                OperationDefinition.Operation.QUERY, new SelectionSet());
        doc.getDefinitions().add(queryOp);

        int counter = 0;
        Map<String, Object> variables = new HashMap<>();
        Map<DataFetchingEnvironment, Field> clonedFields = new HashMap<>();
        for (DataFetchingEnvironment environment : environments) {
            Field original = findFieldWithName(environment);
            Field field = DocumentCloners.clone(original);
            field.setAlias(field.getName() + ++counter);
            clonedFields.put(environment, field);

            trimFieldSelection(schemaSource, environment, field);

            variables.putAll(collectVariables(environment, field, link, counter));
            processForOperations(environment, queryOp, field, link, counter);

            // todo: handle duplicate fragments
            List<Definition> fragmentDefinitions = processForFragments(environment, field);
            doc.getDefinitions().addAll(fragmentDefinitions);
        }

        GraphQLQueryPrinter printer = new GraphQLQueryPrinter();
        String query = printer.print(doc);
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName("Batch")
                .variables(variables)
                .build();

        return schemaSource.query(input, environments.get(0).getContext())
                .thenApply(result -> {
                            List<Object> queryResults = new ArrayList<>();
                            Map<String, Object> data = result.getData();
                            for (DataFetchingEnvironment environment : environments) {
                                Field field = clonedFields.get(environment);
                                Object fieldData = data.getOrDefault(field.getAlias(), null);

                                queryResults.add(new DataFetcherResult<>(fieldData, result.getErrors().stream()
                                        .filter(e -> e.getPath() == null || e.getPath().isEmpty()
                                                || field.getAlias().equals(e.getPath().get(0)))
                                        .map(RelativeGraphQLError::new)
                                        .collect(Collectors.toList())
                                ));
                            }
                            return queryResults;
                        }
                );
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
                    type = ((GraphQLObjectType) parentType).getFieldDefinition(node.getName()).getType();
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
                        visit(child);
                    }
                    parentType = lastParentType;
                }
            }
        }.visit(field);
    }

    /**
     * Collects variable values
     */
    private Map<String, Object> collectVariables(DataFetchingEnvironment environment, Field field, Link link, int counter) {
        Map<String, Object> variables = new HashMap<>();
        if (link != null) {
            //noinspection unchecked
            Object source = environment.getSource();
            while (!(source instanceof Map)) {
                if (source instanceof CompletableFuture) {
                    try {
                        source = ((CompletableFuture) source).get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                } else if (source instanceof DataFetcherResult) {
                    source = ((DataFetcherResult)source).getData();
                } else {
                    throw new IllegalArgumentException("Unexpected parent type");
                }
            }
            //noinspection unchecked
            variables.put(link.getArgumentName() + 1, ((Map<String, String>)source).get(field.getName()));
        } else if (!field.getArguments().isEmpty()) {
            for (Argument arg : referenceArguments(field)) {
                variables.put(arg.getName() + 1, environment.getArgument(arg.getName()));
            }
        }

        List<Argument> renamedArguments = field.getArguments().stream()
                .map(a -> {
                    Value value = a.getValue();
                    if (value instanceof VariableReference) {
                        value = new VariableReference(((VariableReference)value).getName() + counter);
                    }
                    return new Argument(a.getName(), value);
                }).collect(Collectors.toList());
        field.setArguments(renamedArguments);

        return Collections.unmodifiableMap(variables);
    }

    private List<Argument> referenceArguments(Field field) {
        return field.getArguments().stream()
                .filter(a -> a.getValue() instanceof VariableReference)
                .collect(Collectors.toList());
    }

    /**
     * Builds operation definitions, mainly the query.  Also modifies the field to set arguments
     */
    private void processForOperations(DataFetchingEnvironment environment, OperationDefinition query, Field field, Link link, int counter) {
        List<VariableDefinition> variableTypes = new ArrayList<>();
        if (link != null) {
            field.setArguments(singletonList(
                    new Argument(link.getArgumentName(), new VariableReference(link.getArgumentName() + counter))
            ));
            variableTypes = singletonList(new VariableDefinition(link.getArgumentName() + counter, new TypeName("String")));
            field.setName(link.getTargetField());
        } else if (!field.getArguments().isEmpty()) {
            List<InputValueDefinition> inputValueDefinitions = environment.getFieldTypeInfo().getFieldDefinition()
                    .getDefinition().getInputValueDefinitions();
            for (Argument arg : referenceArguments(field)) {
                variableTypes.add(new VariableDefinition(arg.getName() + counter,
                        findArgumentVariableType(inputValueDefinitions, arg.getName())));
            }
        }

        query.getVariableDefinitions().addAll(variableTypes);
        query.getSelectionSet().getSelections().add(field);
    }

    /**
     * Ensures any referenced fragments are included in the query
     */
    private List<Definition> processForFragments(DataFetchingEnvironment environment, Field field) {
        List<Definition> result = new ArrayList<>();
        new GraphQLQueryVisitor() {
            @Override
            protected void visitFragmentSpread(FragmentSpread node) {
                FragmentDefinition fragmentDefinition = environment.getFragmentsByName().get(node.getName());
                result.add(fragmentDefinition);
                super.visitFragmentSpread(node);
            }
        }.visit(field);
        return result;
    }

    private Type findArgumentVariableType(List<InputValueDefinition> inputValueDefinitions, String argName) {
        return inputValueDefinitions.stream()
                .filter(d -> d.getName().equals(argName))
                .findFirst()
                .orElseThrow(IllegalStateException::new)
                .getType();
    }

    private Optional<Link> getLink(Collection<Link> links, String typeName, String fieldName) {
        return links.stream()
                .filter(l -> l.getSourceType().equals(typeName) && l.getSourceField().equals(fieldName))
                .findFirst();
    }
}
