package com.atlassian.braid;

import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;

/**
 * A visitor for GraphQL query {@link Document} related objects.
 */
@SuppressWarnings("WeakerAccess")
public class GraphQLQueryVisitor {
    @SuppressWarnings("StatementWithEmptyBody")
    void visit(final Node node) {
        if (node instanceof Document) {
            visitDocument((Document) node);
        } else if (node instanceof OperationDefinition) {
            visitOperationDefinition((OperationDefinition) node);
        } else if (node instanceof FragmentDefinition) {
            visitFragmentDefinition((FragmentDefinition) node);
        } else if (node instanceof VariableDefinition) {
            visitVariableDefinition((VariableDefinition) node);
        } else if (node instanceof SelectionSet) {
            visitSelectionSet((SelectionSet) node);
        } else if (node instanceof Field) {
            visitField((Field) node);
        } else if (node instanceof InlineFragment) {
            visitInlineFragment((InlineFragment) node);
        } else if (node instanceof FragmentSpread) {
            visitFragmentSpread((FragmentSpread) node);
        } else if (node != null) {
            throw new RuntimeException("Unknown type of node " + node + " at: " + node.getSourceLocation());
        } else {
            // node is null, ignore
        }
    }

    protected void visitDocument(final Document node) {
        for (final Definition definition : node.getDefinitions()) {
            visit(definition);
        }
    }

    protected void visitOperationDefinition(final OperationDefinition node) {
        for (final VariableDefinition variable : node.getVariableDefinitions()) {
            visitVariableDefinition(variable);
        }
        visitSelectionSet(node.getSelectionSet());
    }

    protected void visitFragmentDefinition(final FragmentDefinition node) {
        visitSelectionSet(node.getSelectionSet());
        for (final Directive directive : node.getDirectives()) {
            visit(directive);
        }
    }

    protected void visitVariableDefinition(@SuppressWarnings("unused") final VariableDefinition node) {
    }

    protected void visitSelectionSet(final SelectionSet node) {
        if (node == null) {
            return;
        }
        for (final Node child : node.getChildren()) {
            visit(child);
        }
    }

    protected void visitField(final Field node) {
        visitSelectionSet(node.getSelectionSet());
    }

    protected void visitInlineFragment(final InlineFragment node) {
        visitSelectionSet(node.getSelectionSet());
    }

    protected void visitFragmentSpread(final FragmentSpread node) {
        for (final Directive directive : node.getDirectives()) {
            visit(directive);
        }
    }
}
