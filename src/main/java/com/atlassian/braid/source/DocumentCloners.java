package com.atlassian.braid.source;

import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.Field;
import graphql.language.FloatValue;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Clones schema elements for the purposes of creating subgraph requests
 */
@SuppressWarnings("WeakerAccess")
class DocumentCloners {

    private DocumentCloners() {
    }

    static Field clone(Field original) {
        if (original == null) {
            return null;
        }
        Field cloned = new Field(original.getName());
        cloned.setAlias(original.getAlias());
        cloned.setArguments(cloneList(original.getArguments(), DocumentCloners::clone));
        cloned.setDirectives(cloneList(original.getDirectives(), DocumentCloners::clone));
        cloned.setSelectionSet(clone(original.getSelectionSet()));
        return cloned;
    }

    static SelectionSet clone(SelectionSet original) {
        if (original == null) {
            return null;
        }
        return new SelectionSet(cloneList(original.getSelections(), DocumentCloners::clone));
    }

    static Selection clone(Selection original) {
        if (original == null) {
            return null;
        }
        if (original instanceof Field) {
            return clone((Field) original);
        } else if (original instanceof FragmentSpread) {
            return clone((FragmentSpread) original);
        } else if ((original instanceof InlineFragment)) {
            return clone((InlineFragment) original);
        }
        throw new IllegalArgumentException("Unexpected type for selection: " + original.getClass());
    }

    static InlineFragment clone(InlineFragment original) {
        if (original == null) {
            return null;
        }
        InlineFragment fragment = new InlineFragment(original.getTypeCondition());
        fragment.setDirectives(cloneList(original.getDirectives(), DocumentCloners::clone));
        fragment.setSelectionSet(clone(original.getSelectionSet()));
        return fragment;
    }

    static FragmentSpread clone(FragmentSpread original) {
        if (original == null) {
            return null;
        }
        FragmentSpread spread = new FragmentSpread(original.getName());
        spread.setDirectives(cloneList(original.getDirectives(), DocumentCloners::clone));
        return spread;
    }

    static Directive clone(Directive original) {
        if (original == null) {
            return null;
        }
        return new Directive(
                original.getName(),
                cloneList(original.getArguments(), DocumentCloners::clone)
        );
    }

    static Argument clone(Argument original) {
        if (original == null) {
            return null;
        }
        return new Argument(
                original.getName(),
                clone(original.getValue())
        );
    }

    static Value clone(Value original) {
        if (original == null) {
            return null;
        } else if (original instanceof StringValue) {
            return new StringValue(((StringValue) original).getValue());
        } else if (original instanceof IntValue) {
            return new IntValue(((IntValue) original).getValue());
        } else if (original instanceof ArrayValue) {
            return new ArrayValue(cloneList(((ArrayValue) original).getValues(), DocumentCloners::clone));
        } else if (original instanceof BooleanValue) {
            return new BooleanValue(((BooleanValue) original).isValue());
        } else if (original instanceof EnumValue) {
            return new EnumValue(((EnumValue) original).getName());
        } else if (original instanceof FloatValue) {
            return new FloatValue(((FloatValue) original).getValue());
        } else if (original instanceof NullValue) {
            return original;
        } else if (original instanceof ObjectValue) {
            return new ObjectValue(cloneList(((ObjectValue) original).getObjectFields(), DocumentCloners::clone));
        } else if (original instanceof VariableReference) {
            return new VariableReference(((VariableReference) original).getName());
        }
        throw new IllegalArgumentException("Invalid value to clone: " + original);
    }

    static ObjectField clone(ObjectField original) {
        if (original == null) {
            return null;
        }
        return new ObjectField(original.getName(), clone(original.getValue()));
    }

    private static <T> List<T> cloneList(List<T> original, Function<T, T> cloner) {
        if (original == null) {
            return null;
        } else {
            return original.stream().map(cloner).collect(Collectors.toList());
        }
    }
}
