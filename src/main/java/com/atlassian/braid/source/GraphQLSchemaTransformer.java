package com.atlassian.braid.source;

import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A visitor for GraphQL schema {@link Document} related objects.
 */
@SuppressWarnings("WeakerAccess")
class GraphQLSchemaTransformer {
    @SuppressWarnings({"unchecked", "StatementWithEmptyBody"})
    <T extends Node> T visit(final T node) {
        T result = null;
        if (node instanceof Document) {
            result = (T) visitDocument((Document) node);
        } else if (node instanceof SchemaDefinition) {
            result = (T) visitSchemaDefinition((SchemaDefinition) node);
        } else if (node instanceof OperationTypeDefinition) {
            result = (T) visitOperationTypeDefinition((OperationTypeDefinition) node);
        } else if (node instanceof ObjectTypeDefinition) {
            result = (T) visitObjectTypeDefinition((ObjectTypeDefinition) node);
        } else if (node instanceof FieldDefinition) {
            result = (T) visitFieldDefinition((FieldDefinition) node);
        } else if (node instanceof EnumTypeDefinition) {
            result = (T) visitEnumTypeDefinition((EnumTypeDefinition) node);
        } else if (node instanceof EnumValueDefinition) {
            result = (T) visitEnumValueDefinition((EnumValueDefinition) node);
        } else if (node instanceof ScalarTypeDefinition) {
            result = (T) visitScalarTypeDefinition((ScalarTypeDefinition) node);
        } else if (node instanceof UnionTypeDefinition) {
            result = (T) visitUnionTypeDefinition((UnionTypeDefinition) node);
        } else if (node instanceof InterfaceTypeDefinition) {
            result = (T) visitInterfaceTypeDefinition((InterfaceTypeDefinition) node);
        } else if (node instanceof InputObjectTypeDefinition) {
            result = (T) visitInputObjectTypeDefinition((InputObjectTypeDefinition) node);
        } else if (node instanceof InputValueDefinition) {
            result = (T) visitInputValueDefinition((InputValueDefinition) node);    
        } else if (node instanceof Directive) {
            result = (T) visitDirective((Directive) node);
        } else if (node instanceof Argument) {
            result = (T) visitArgument((Argument) node);
        } else if (node instanceof Value) {
            result = (T) visitValue((Value) node);
        } else if (node instanceof NonNullType) {
            result = (T) visitNonNullType((NonNullType) node);
        } else if (node instanceof ListType) {
            result = (T) visitListType((ListType) node);
        } else if (node instanceof TypeName) {
            result = (T) visitTypeName((TypeName) node);
        } else if (node != null) {
            throw new RuntimeException("Unknown type of node " + node.getClass().getSimpleName() + " at: " + node.getSourceLocation());
        } else {
            // node is null, ignore
        }
        return result;
    }

    private InputValueDefinition visitInputValueDefinition(InputValueDefinition node) {
        return new InputValueDefinition(
                node.getName(),
                visitType(node.getType()),
                visitValue(node.getDefaultValue()),
                visitNodes(node.getDirectives())
        );
    }

    protected TypeName visitTypeName(TypeName node) {
        return node;
    }

    protected ListType visitListType(ListType node) {
        return new ListType(visit(node));
    }

    protected NonNullType visitNonNullType(NonNullType node) {
        return new NonNullType(visit(node));
    }

    protected ScalarTypeDefinition visitScalarTypeDefinition(ScalarTypeDefinition node) {
        return new ScalarTypeDefinition(
                node.getName(),
                visitNodes(node.getDirectives())
        );
    }

    protected Value visitValue(Value node) {
        return node;
    }

    protected Argument visitArgument(Argument node) {
        return new Argument(
                node.getName(),
                visit(node.getValue())
        );
    }

    protected Directive visitDirective(Directive node) {
        return new Directive(
                node.getName(),
                visitNodes(node.getArguments())
        );
    }

    protected InputObjectTypeDefinition visitInputObjectTypeDefinition(InputObjectTypeDefinition node) {
        return new InputObjectTypeDefinition(
                node.getName(),
                visitNodes(node.getDirectives()),
                visitNodes(node.getInputValueDefinitions())
        );
    }

    protected InterfaceTypeDefinition visitInterfaceTypeDefinition(InterfaceTypeDefinition node) {
        return new InterfaceTypeDefinition(
                node.getName(),
                visitNodes(node.getFieldDefinitions()),
                visitNodes(node.getDirectives())
        );
    }

    protected UnionTypeDefinition visitUnionTypeDefinition(UnionTypeDefinition node) {
        return new UnionTypeDefinition(
                node.getName(),
                visitNodes(node.getDirectives()),
                visitNodes(node.getMemberTypes())
        );
    }

    protected EnumValueDefinition visitEnumValueDefinition(EnumValueDefinition node) {
        return new EnumValueDefinition(
                node.getName(),
                visitNodes(node.getDirectives())
        );
    }

    protected EnumTypeDefinition visitEnumTypeDefinition(EnumTypeDefinition node) {
        return new EnumTypeDefinition(
                node.getName(),
                visitNodes(node.getEnumValueDefinitions()),
                visitNodes(node.getDirectives())
        );
    }

    protected FieldDefinition visitFieldDefinition(FieldDefinition node) {
        return new FieldDefinition(
                node.getName(),
                visitType(node.getType()),
                visitNodes(node.getInputValueDefinitions()),
                visitNodes(node.getDirectives())
        );
    }

    protected Type visitType(Type type) {
        return type;
    }

    protected ObjectTypeDefinition visitObjectTypeDefinition(ObjectTypeDefinition node) {
        return new ObjectTypeDefinition(
                node.getName(),
                visitNodes(node.getImplements()),
                visitNodes(node.getDirectives()),
                visitNodes(node.getFieldDefinitions())
        );
    }

    protected OperationTypeDefinition visitOperationTypeDefinition(OperationTypeDefinition node) {
        return new OperationTypeDefinition(
                node.getName(),
                visitType(node.getType())
        );
    }

    protected SchemaDefinition visitSchemaDefinition(SchemaDefinition node) {
        return new SchemaDefinition(
                visitNodes(node.getDirectives()),
                visitNodes(node.getOperationTypeDefinitions())
        );
    }

    protected Document visitDocument(final Document node) {
        return new Document(node.getDefinitions().stream().map(this::visit).collect(Collectors.toList()));
    }

    protected <T extends Node> List<T> visitNodes(List<T> nodes) {
        return nodes.stream().map(this::visit).collect(Collectors.toList());
    }
}
