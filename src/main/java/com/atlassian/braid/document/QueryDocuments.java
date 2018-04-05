package com.atlassian.braid.document;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;

import java.util.List;
import java.util.Map;

import static com.atlassian.braid.java.util.BraidLists.groupByType;
import static com.atlassian.braid.java.util.BraidPreconditions.checkState;

final class QueryDocuments {

    private static final Class[] ROOT_DEFINITION_TYPES = {OperationDefinition.class, FragmentDefinition.class};

    private QueryDocuments() {
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Definition>[] getRootDefinitionTypes() {
        return ROOT_DEFINITION_TYPES;
    }

    static Map<Class<?>, List<Definition>> groupRootDefinitionsByType(Document doc) {
        final Map<Class<?>, List<Definition>> definitionsByType = groupByType(doc.getDefinitions());
        checkState(definitionsByType.size() <= ROOT_DEFINITION_TYPES.length);
        return definitionsByType;
    }
}
