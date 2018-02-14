package com.atlassian.braid.graphql.language;

import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.EnumValue;
import graphql.language.Field;
import graphql.language.FloatValue;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.IntValue;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;

import java.util.List;
import java.util.function.Consumer;

/**
 * A printer for GraphQL query {@link Document} objects.
 */
final class GraphQLQueryPrinter {
    private static final int INDENT_WIDTH = 4;

    String print(final Node node) {
        return new Printer(node).print();
    }

    private static class Printer {
        private final StringBuilder str = new StringBuilder();
        private final Node root;
        private int indentLevel = 0;

        Printer(final Node root) {
            this.root = root;
        }

        public String print() {
            print(root);
            return str.toString();
        }

        private <T extends Node> void join(final List<T> nodes, final String delimiter) {
            final int size = nodes.size();
            for (int i = 0; i < size; i++) {
                T node = nodes.get(i);
                print(node);
                if (i + 1 != size) {
                    str.append(delimiter);
                }
            }
        }

        private <T> void wrap(final String start, final T obj, final String end) {
            wrap(start, obj, end, str::append);
        }

        private <T> void wrap(final String start, final T obj, final String end, final Consumer<T> inner) {
            if (obj instanceof List && ((List) obj).isEmpty()) {
                return;
            }
            str.append(start);
            inner.accept(obj);
            str.append(end);
        }

        private String getNewLine() {
            final StringBuilder str = new StringBuilder();
            str.append("\n");
            for (int i = 0; i < indentLevel; i++) {
                for (int j = 0; j < INDENT_WIDTH; j++) {
                    str.append(" ");
                }
            }
            return str.toString();
        }

        private void line() {
            line(1);
        }

        private void line(int count) {
            for (int i = 0; i < count; i++) {
                str.append(getNewLine());
            }
        }

        private void print(final Node node) {
            if (node instanceof Document) {
                print((Document) node);
            } else if (node instanceof OperationDefinition) {
                print((OperationDefinition) node);
            } else if (node instanceof FragmentDefinition) {
                print((FragmentDefinition) node);
            } else if (node instanceof VariableDefinition) {
                print((VariableDefinition) node);
            } else if (node instanceof ArrayValue) {
                print((ArrayValue) node);
            } else if (node instanceof BooleanValue) {
                print((BooleanValue) node);
            } else if (node instanceof EnumValue) {
                print((EnumValue) node);
            } else if (node instanceof FloatValue) {
                print((FloatValue) node);
            } else if (node instanceof IntValue) {
                print((IntValue) node);
            } else if (node instanceof ObjectValue) {
                print((ObjectValue) node);
            } else if (node instanceof StringValue) {
                print((StringValue) node);
            } else if (node instanceof VariableReference) {
                print((VariableReference) node);
            } else if (node instanceof ListType) {
                print((ListType) node);
            } else if (node instanceof NonNullType) {
                print((NonNullType) node);
            } else if (node instanceof TypeName) {
                print((TypeName) node);
            } else if (node instanceof Directive) {
                print((Directive) node);
            } else if (node instanceof Argument) {
                print((Argument) node);
            } else if (node instanceof ObjectField) {
                print((ObjectField) node);
            } else if (node instanceof SelectionSet) {
                print((SelectionSet) node);
            } else if (node instanceof Field) {
                print((Field) node);
            } else if (node instanceof InlineFragment) {
                print((InlineFragment) node);
            } else if (node instanceof FragmentSpread) {
                print((FragmentSpread) node);
            } else {
                throw new RuntimeException("unknown type");
            }
        }

        private void print(final Document node) {
            for (final Definition defintition : node.getDefinitions()) {
                print(defintition);
                line(2);
            }
            line();
        }

        private void print(final OperationDefinition node) {
            final String name = node.getName();
            final OperationDefinition.Operation operation = node.getOperation();
            final List<VariableDefinition> variableDefinitions = node.getVariableDefinitions();
            final List<Directive> directives = node.getDirectives();
            final SelectionSet selectionSet = node.getSelectionSet();

            if (name == null && variableDefinitions.isEmpty() && directives.isEmpty() && operation == OperationDefinition.Operation.QUERY) {
                print(selectionSet);
            } else {
                if (operation == OperationDefinition.Operation.QUERY) {
                    str.append("query ");
                } else if (operation == OperationDefinition.Operation.MUTATION) {
                    str.append("mutation ");
                } else {
                    throw new RuntimeException("unsupported operation");
                }
                if (name != null) {
                    str.append(name);
                }
                wrap("(", variableDefinitions, ")", definitions -> {
                    join(definitions, ", ");
                });
                wrap(" ", directives, " ", dirs -> {
                    join(dirs, " ");
                });
                str.append(" ");
                print(selectionSet);
            }
        }

        private void print(final VariableDefinition node) {
            str.append("$");
            str.append(node.getName());
            str.append(": ");
            print(node.getType());

            Value defaultValue = node.getDefaultValue();
            if (defaultValue != null) {
                str.append(" = ");
                print(defaultValue);
            }
        }

        private void print(final Directive node) {
            str.append("@");
            str.append(node.getName());
            wrap("(", node.getArguments(), ")", arguments -> {
                join(arguments, ", ");
            });
        }

        private void print(final SelectionSet node) {
            print(node, false);
        }

        private void print(final SelectionSet node, final boolean space) {
            List<Selection> selections = node.getSelections();
            if (selections.isEmpty()) {
                return;
            }
            str.append(space ? " {" : "{");
            indentLevel++;
            line();
            join(selections, getNewLine());
            indentLevel--;
            line();
            str.append("}");
        }

        private void print(final FragmentDefinition node) {
            str.append("fragment ");
            str.append(node.getName());
            str.append(" on ");
            print(node.getTypeCondition());
            wrap(" ", node.getDirectives(), " ", directives -> {
                join(directives, " ");
            });
            print(node.getSelectionSet(), true);
        }

        private void print(final ArrayValue node) {
            wrap("[", node.getValues(), "]", values -> {
                join(values, ", ");
            });
        }

        private void print(final BooleanValue node) {
            str.append(node.isValue());
        }

        private void print(final EnumValue node) {
            str.append(node.getName());
        }

        private void print(final FloatValue node) {
            str.append(node.getValue());
        }

        private void print(final IntValue node) {
            str.append(node.getValue());
        }

        private void print(final ObjectValue node) {
            wrap("{", node.getObjectFields(), "}", fields -> {
                join(fields, ", ");
            });
        }

        private void print(final StringValue node) {
            wrap("\"", node.getValue(), "\"");
        }

        private void print(final VariableReference node) {
            str.append("$");
            str.append(node.getName());
        }

        private void print(final ListType node) {
            wrap("[", node.getType(), "]", this::print);
        }

        private void print(final NonNullType node) {
            print(node.getType());
            str.append("!");
        }

        private void print(final TypeName node) {
            str.append(node.getName());
        }

        private void print(final Argument node) {
            str.append(node.getName());
            str.append(": ");
            print(node.getValue());
        }

        private void print(final Field node) {
            String alias = node.getAlias();
            if (alias != null) {
                str.append(alias);
                str.append(": ");
            }

            str.append(node.getName());
            List<Argument> arguments = node.getArguments();
            wrap("(", arguments, ")", args -> {
                join(args, ", ");
            });
            join(node.getDirectives(), " ");

            SelectionSet selectionSet = node.getSelectionSet();
            if (selectionSet != null) {
                print(selectionSet, true);
            }
        }

        private void print(final ObjectField node) {
            str.append(node.getName());
            str.append(": ");
            print(node.getValue());
        }

        private void print(final InlineFragment node) {
            str.append("... on ");
            str.append(node.getTypeCondition().getName());
            str.append(" ");
            join(node.getDirectives(), " ");
            print(node.getSelectionSet());
        }

        private void print(final FragmentSpread node) {
            str.append("...");
            str.append(node.getName());
            join(node.getDirectives(), " ");
        }
    }
}