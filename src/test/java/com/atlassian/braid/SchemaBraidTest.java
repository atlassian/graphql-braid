package com.atlassian.braid;

import com.google.common.collect.ImmutableMap;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.Map;
import java.util.function.Function;

import static com.atlassian.braid.Util.parseDocument;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 */
public class SchemaBraidTest {

    private Function<ExecutionInput, Object> barQueryExecutor;
    private ExecutionInput barInput;
    private Function<ExecutionInput, Object> fooQueryExecutor;
    private ExecutionInput fooInput;
    private GraphQLSchema schema;

    @Before
    public void setUp() {
        barQueryExecutor = mock(Function.class);
        barInput = ExecutionInput.newExecutionInput()
                .query("query Foobar($id: String) {\n" +
                        "    topbar(id: $id) {\n" +
                        "        id\n" +
                        "        title\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Foo.bar")
                .variables(singletonMap("id", "barid"))
                .build();
        when(barQueryExecutor.apply(argThat(matchesInput(barInput)))).thenReturn(
                ImmutableMap.of("id", "barid", "title", "Bar")
        );
        fooQueryExecutor = mock(Function.class);
        fooInput = ExecutionInput.newExecutionInput()
                .query("query Queryfoo($id: String) {\n" +
                        "    foo(id: $id) {\n" +
                        "        id\n" +
                        "        name\n" +
                        "        bar\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Query.foo")
                .variables(singletonMap("id", "fooid"))
                .build();
        when(fooQueryExecutor.apply(argThat(matchesInput(fooInput)))).thenReturn(
                ImmutableMap.of("id", "fooid", "name", "Foo", "bar", "barid")
        );

        SchemaBraid weaver = new SchemaBraid();
        schema = weaver.braid(
                new LocalDataSource("bar", parseDocument("/com/atlassian/braid/bar.graphql"), barQueryExecutor),

                new LocalDataSource("foo", parseDocument("/com/atlassian/braid/foo.graphql"), singletonList(
                        Link
                                .from("Foo", "bar")
                                .to("bar", "Bar")
                                .targetField("topbar")
                                .targetArgument("id")
                ), fooQueryExecutor));
    }

    @Test
    public void testWeaver() {
        GraphQL graphql = new GraphQL.Builder(schema).build();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("query($id: String!) { foo(id: $id) { id, name, bar { id, title } } }")
                .variables(singletonMap("id", "fooid")));
        assertEquals(emptyList(), result.getErrors());
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));

        assertEquals(data.get("foo").get("name"), "Foo");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("title"), "Bar");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("id"), "barid");
    }

    @Test
    public void testWeaverWithFragment() {
        barQueryExecutor = mock(Function.class);
        barInput = ExecutionInput.newExecutionInput()
                .query("query Foobar($id: String) {\n" +
                        "    topbar(id: $id) {\n" +
                        "        ...barFields\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "fragment barFields on Bar {\n" +
                        "    id\n" +
                        "    title\n" +
                        "}\n" +
                        "\n" +
                        "\n")
                .operationName("Foo.bar")
                .variables(singletonMap("id", "barid"))
                .build();
        when(barQueryExecutor.apply(argThat(matchesInput(barInput)))).thenReturn(
                ImmutableMap.of("id", "barid", "title", "Bar")
        );
        schema = new SchemaBraid().braid(
                new LocalDataSource("bar", parseDocument("/com/atlassian/braid/bar.graphql"), barQueryExecutor),

                new LocalDataSource("foo", parseDocument("/com/atlassian/braid/foo.graphql"), singletonList(
                        Link
                                .from("Foo", "bar")
                                .to("bar", "Bar")
                                .targetField("topbar")
                                .targetArgument("id")
                ), fooQueryExecutor));

        GraphQL graphql = new GraphQL.Builder(schema).build();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("query($id: String!) { foo(id: $id) { id, name, bar { ...barFields } } }\n" +
                        "fragment barFields on Bar {\n" +
                        "  id\n" +
                        "  title\n" +
                        "}")
                .variables(singletonMap("id", "fooid")));
        assertEquals(emptyList(), result.getErrors());
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));

        assertEquals(data.get("foo").get("name"), "Foo");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("title"), "Bar");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("id"), "barid");
    }

    private static ExecutionInputMatcher matchesInput(ExecutionInput input) {
        return new ExecutionInputMatcher(input);
    }

    private static class ExecutionInputMatcher implements ArgumentMatcher<ExecutionInput> {

        private final ExecutionInput input;

        private ExecutionInputMatcher(ExecutionInput input) {
            this.input = input;
        }

        @Override
        public boolean matches(ExecutionInput arg) {
            return arg.toString().equals(input.toString());
        }
    }
}
