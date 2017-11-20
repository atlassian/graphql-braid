package com.atlassian.braid;


import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import static com.atlassian.braid.Util.parseRegistry;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QueryExecutorTest {

    @Test
    public void testTrimFields() {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

        SchemaSource source = mock(SchemaSource.class);
        when(source.getLinks()).thenReturn(emptyList());

        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
        when(env.getFieldType()).thenReturn(schema.getObjectType("Bar"));
        QueryExecutor queryExecutor = new QueryExecutor();

        Document query = new Parser().parseDocument("query {foo(id:fooid){id, title, baz, mylist {name}}}");
        Field fooField = (Field) ((OperationDefinition)query.getDefinitions().get(0)).getSelectionSet().getSelections().stream().filter(d -> d instanceof Field && ((Field)d).getName().equals("foo")).findFirst().get();
        queryExecutor.trimFieldSelection(source, env, fooField);
    }
}
