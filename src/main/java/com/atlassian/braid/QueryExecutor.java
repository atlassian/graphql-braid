package com.atlassian.braid;

import graphql.ExecutionInput;
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
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonList;

/**
 * Executes a query against the data source
 */
class QueryExecutor {
    Object query(DataSource dataSource, DataFetchingEnvironment environment, Link link) {
        Document doc = new Document();
        Field original = environment.getFields().stream().filter(f -> environment.getFieldDefinition().getName()
                .equals(f.getName())).findFirst().orElseThrow(IllegalArgumentException::new);
        Field field = DocumentCloner.clone(original);

        trimFieldSelection(dataSource, environment, field);

        Map<String, Object> variables = collectVariables(environment, field, link);

        List<Definition> operationDefinitions = processForOperations(environment, field, link);
        List<Definition> fragmentDefinitions = processForFragments(environment, field);
        doc.getDefinitions().addAll(operationDefinitions);
        doc.getDefinitions().addAll(fragmentDefinitions);


        GraphQLQueryPrinter printer = new GraphQLQueryPrinter();
        String query = printer.print(doc);
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(getOperationName(environment))
                .variables(variables)
                .build();
        return dataSource.query(input);
    }

    private String getOperationName(DataFetchingEnvironment environment) {
        return environment.getParentType().getName() + "." + environment.getFieldDefinition().getName();
    }

    /**
     * Ensures we only ask for fields the data source supports
     */
    private void trimFieldSelection(DataSource dataSource, DataFetchingEnvironment environment, Field field) {
        new GraphQLQueryVisitor() {
            GraphQLOutputType parentType = null;
            GraphQLOutputType lastFieldType = null;

            @Override
            protected void visitField(Field node) {
                if (node == field) {
                    lastFieldType = environment.getFieldType();
                } else {
                    getLink(dataSource.getLinks(), parentType.getName(), node.getName())
                            .ifPresent(l -> node.setSelectionSet(null));
                    lastFieldType = ((GraphQLObjectType) parentType).getFieldDefinition(node.getName()).getType();
                }
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
    private Map<String, Object> collectVariables(DataFetchingEnvironment environment, Field field, Link link) {
        Map<String, Object> variables = new HashMap<>();
        if (link != null) {
            //noinspection unchecked
            variables.put(link.getArgumentName(), ((Map<String, String>) environment.getSource()).get(field.getName()));
        } else if (!field.getArguments().isEmpty()) {
            for (Argument arg : field.getArguments()) {
                variables.put(arg.getName(), environment.getArgument(arg.getName()));
            }
        }

        return variables;
    }

    /**
     * Builds operation definitions, mainly the query.  Also modifies the field to set arguments
     */
    private List<Definition> processForOperations(DataFetchingEnvironment environment, Field field, Link link) {
        List<VariableDefinition> variableTypes = new ArrayList<>();
        if (link != null) {
            field.setArguments(singletonList(
                    new Argument(link.getArgumentName(), new VariableReference(link.getArgumentName()))
            ));
            variableTypes = singletonList(new VariableDefinition(link.getArgumentName(), new TypeName("String")));
            field.setName(link.getTargetField());
        } else if (!field.getArguments().isEmpty()) {
            List<InputValueDefinition> inputValueDefinitions = environment.getFieldTypeInfo().getFieldDefinition()
                    .getDefinition().getInputValueDefinitions();
            for (Argument arg : field.getArguments()) {
                variableTypes.add(new VariableDefinition(arg.getName(),
                        findArgumentVariableType(inputValueDefinitions, arg)));
            }
        }

        return singletonList(new OperationDefinition(
                environment.getParentType().getName() + environment.getFieldDefinition().getName(),
                OperationDefinition.Operation.QUERY,
                variableTypes,
                new SelectionSet(singletonList(field))));
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

    private Type findArgumentVariableType(List<InputValueDefinition> inputValueDefinitions, Argument arg) {
        return inputValueDefinitions.stream().filter(
                d -> d.getName().equals(arg.getName())).findFirst().orElseThrow(IllegalStateException::new).getType();
    }

    private Optional<Link> getLink(List<Link> links, String typeName, String fieldName) {
        return links.stream().filter(l -> l.getSourceType().equals(typeName) && l.getSourceField().equals(fieldName)).findFirst();
    }
}
