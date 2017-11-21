package com.atlassian.braid;

import com.atlassian.braid.source.LocalSchemaSource;
import com.google.common.collect.ImmutableMap;
import graphql.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoaderRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.atlassian.braid.Util.parseRegistry;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class SchemaBraidTest {

    private static final SchemaNamespace FOO = SchemaNamespace.of("foo");
    private static final SchemaNamespace BAR = SchemaNamespace.of("bar");
    private static final SchemaNamespace BAZ = SchemaNamespace.of("baz");

    private Function<ExecutionInput, Object> barQueryExecutor;
    private ExecutionInput barInput;
    private Function<ExecutionInput, Object> fooQueryExecutor;
    private ExecutionInput fooInput;
    private Function<ExecutionInput, Object> bazQueryExecutor;
    private Braid braid;
    private DataLoaderRegistry dataLoaderRegistry;

    @Before
    public void setUp() {
        barQueryExecutor = mock(Function.class);
        barInput = ExecutionInput.newExecutionInput()
                .query("query ($id1: String) {\n" +
                        "    bar1: topbar(id: $id1) {\n" +
                        "        id\n" +
                        "        title\n" +
                        "    }\n" +
                        "}\n" +
                        "\n\n")
                .operationName("Batch")
                .variables(singletonMap("id1", "barid"))
                .build();
        when(barQueryExecutor.apply(argThat(matchesInput(barInput)))).thenReturn(
                singletonMap("bar1", ImmutableMap.of("id", "barid", "title", "Bar"))
        );
        fooQueryExecutor = mock(Function.class);
        fooInput = ExecutionInput.newExecutionInput()
                .query("query ($id1: String) {\n" +
                        "    foo1: foo(id: $id1) {\n" +
                        "        id\n" +
                        "        name\n" +
                        "        bar\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Batch")
                .variables(singletonMap("id1", "fooid"))
                .build();
        when(fooQueryExecutor.apply(argThat(matchesInput(fooInput)))).thenReturn(
                singletonMap("foo1", ImmutableMap.of("id", "fooid", "name", "Foo", "bar", "barid"))
        );

        bazQueryExecutor = mock(Function.class);
        when(bazQueryExecutor.apply(any())).thenReturn(
                singletonMap("baz1", ImmutableMap.of("id", "bazid", "rating", 5)));

        SchemaBraid schemaBraid = new SchemaBraid();
        braid = schemaBraid.braid(
                new LocalSchemaSource(BAR, parseRegistry("/com/atlassian/braid/bar.graphql"), singletonList(
                        Link
                                .from("Bar", "baz")
                                .to(BAZ, "Baz")
                ), barQueryExecutor),

                new LocalSchemaSource(FOO, parseRegistry("/com/atlassian/braid/foo.graphql"), singletonList(
                        Link
                                .from("Foo", "bar")
                                .to(BAR, "Bar")
                                .targetField("topbar")
                                .targetArgument("id")
                ), fooQueryExecutor),
                new LocalSchemaSource(BAZ, parseRegistry("/com/atlassian/braid/baz.graphql"), bazQueryExecutor));
        dataLoaderRegistry = braid.newDataLoaderRegistry();
    }

    @Test
    public void testBraid() {
        GraphQL graphql = newGraphQL();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("query($id: String!) { foo(id: $id) { id, name, bar { id, title } } }")
                .variables(singletonMap("id", "fooid")).context(new DefaultBraidContext(dataLoaderRegistry)));
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));

        assertEquals(emptyList(), result.getErrors());

        assertEquals(data.get("foo").get("name"), "Foo");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("title"), "Bar");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("id"), "barid");
    }

    private GraphQL newGraphQL() {
        return new GraphQL.Builder(braid.getSchema())
                .instrumentation(new DataLoaderDispatcherInstrumentation(dataLoaderRegistry))
                .build();
    }

    @Test
    public void testBraidWithSchemaSourceError() {
        reset(fooQueryExecutor, barQueryExecutor, bazQueryExecutor);
        fooInput = ExecutionInput.newExecutionInput()
                .query("query  {\n" +
                        "    foo1: foo(id: \"fooid\") {\n" +
                        "        id\n" +
                        "        name\n" +
                        "        bar\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Batch")
                .build();
        when(fooQueryExecutor.apply(argThat(matchesInput(fooInput)))).thenReturn(
                singletonMap("foo1", ImmutableMap.of("id", "fooid", "name", "Foo", "bar", "barid"))
        );

        barInput = ExecutionInput.newExecutionInput()
                .query("query ($id1: String) {\n" +
                        "    bar1: topbar(id: $id1) {\n" +
                        "        id\n" +
                        "        title\n" +
                        "        baz\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Batch")
                .variables(singletonMap("id1", "barid"))
                .build();
        when(barQueryExecutor.apply(argThat(matchesInput(barInput)))).thenReturn(
                singletonMap("bar1", ImmutableMap.of("id", "barid", "title", "Bar", "baz", "bazid"))
        );
        when(bazQueryExecutor.apply(any())).thenReturn(new DataFetcherResult<Map>(
                singletonMap("baz1", new HashMap<String, String>() {{
                    put("id", "bazid");
                    put("rating", null);
                }}),
                singletonList(new StaticGraphQLError("bad rating", asList("baz1", "rating")))
        ));

        GraphQL graphql = newGraphQL();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("{ foo(id: \"fooid\") { id, name, bar { id, title, baz { id, rating } } } }")
                .context(new DefaultBraidContext(dataLoaderRegistry)));
        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));

        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).isEqualTo("bad rating");
        assertThat(error.getPath()).isEqualTo(asList("foo", "bar", "baz", "rating"));
        assertThat(error.getErrorType()).isEqualTo(ErrorType.DataFetchingException);

        Map<String, Map<String, Map<String, Map<String, Object>>>> data = result.getData();

        assertEquals(data.get("foo").get("name"), "Foo");

        assertEquals(data.get("foo").get("bar").get("baz").get("rating"), null);
        assertEquals(data.get("foo").get("bar").get("baz").get("id"), "bazid");
    }

    @Test
    public void testBraidWithInlineArgument() {
        reset(fooQueryExecutor);
        fooInput = ExecutionInput.newExecutionInput()
                .query("query  {\n" +
                        "    foo1: foo(id: \"fooid\") {\n" +
                        "        id\n" +
                        "        name\n" +
                        "        bar\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "\n")
                .operationName("Batch")
                .build();
        when(fooQueryExecutor.apply(argThat(matchesInput(fooInput)))).thenReturn(
                singletonMap("foo1", ImmutableMap.of("id", "fooid", "name", "Foo", "bar", "barid"))
        );

        GraphQL graphql = newGraphQL();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("{ foo2: foo(id: \"fooid\") { id, name, bar { id, title } } }").context(new DefaultBraidContext(dataLoaderRegistry)));
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));

        assertEquals(emptyList(), result.getErrors());

        assertEquals(data.get("foo2").get("name"), "Foo");
        assertEquals(((Map<String, String>) data.get("foo2").get("bar")).get("title"), "Bar");
        assertEquals(((Map<String, String>) data.get("foo2").get("bar")).get("id"), "barid");
    }

    @Test
    public void testBraidWithExistingTypes() {
        reset(fooQueryExecutor);
        fooInput = ExecutionInput.newExecutionInput()
                .query("query  {\n" +
                        "    foo1: foo(id: \"fooid\") {\n" +
                        "        id\n" +
                        "        name\n" +
                        "        bar\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Batch")
                .build();
        when(fooQueryExecutor.apply(argThat(matchesInput(fooInput)))).thenReturn(
                singletonMap("foo1", ImmutableMap.of("id", "fooid", "name", "Foo", "bar", "barid"))
        );

        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/existing.graphql");
        SchemaBraid schemaBraid = new SchemaBraid();
        braid = schemaBraid.braid(registry, RuntimeWiring.newRuntimeWiring(),
                new LocalSchemaSource(BAR, parseRegistry("/com/atlassian/braid/bar.graphql"), singletonList(
                        Link
                                .from("Bar", "baz")
                                .to(BAZ, "Baz")
                ), barQueryExecutor),

                new LocalSchemaSource(FOO, parseRegistry("/com/atlassian/braid/foo.graphql"), singletonList(
                        Link
                                .from("Foo", "bar")
                                .to(BAR, "Bar")
                                .targetField("topbar")
                                .targetArgument("id")
                ), fooQueryExecutor),
                new LocalSchemaSource(BAZ, parseRegistry("/com/atlassian/braid/baz.graphql"), bazQueryExecutor));
        dataLoaderRegistry = braid.newDataLoaderRegistry();

        GraphQL graphql = newGraphQL();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("{ foo(id: \"fooid\") { id, name, bar { id, title } } }")
                .context(new DefaultBraidContext(dataLoaderRegistry)));
        assertEquals(emptyList(), result.getErrors());
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));

        assertEquals(data.get("foo").get("name"), "Foo");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("title"), "Bar");
        assertEquals(((Map<String, String>) data.get("foo").get("bar")).get("id"), "barid");
    }

    @Test
    public void testBraidWithFragment() {
        barQueryExecutor = mock(Function.class);
        barInput = ExecutionInput.newExecutionInput()
                .query("query ($id1: String) {\n" +
                        "    bar1: topbar(id: $id1) {\n" +
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
                .operationName("Batch")
                .variables(singletonMap("id1", "barid"))
                .build();
        when(barQueryExecutor.apply(argThat(matchesInput(barInput)))).thenReturn(
                singletonMap("bar1", ImmutableMap.of("id", "barid", "title", "Bar"))
        );
        braid = new SchemaBraid().braid(
                new LocalSchemaSource(BAR, parseRegistry("/com/atlassian/braid/bar.graphql"), barQueryExecutor),

                new LocalSchemaSource(FOO, parseRegistry("/com/atlassian/braid/foo.graphql"), singletonList(
                        Link
                                .from("Foo", "bar")
                                .to(BAR, "Bar")
                                .targetField("topbar")
                                .targetArgument("id")
                ), fooQueryExecutor));
        dataLoaderRegistry = braid.newDataLoaderRegistry();

        GraphQL graphql = newGraphQL();
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query("query($id: String!) { foo(id: $id) { id, name, bar { ...barFields } } }\n" +
                        "fragment barFields on Bar {\n" +
                        "  id\n" +
                        "  title\n" +
                        "}")
                .variables(singletonMap("id", "fooid"))
                .context(new DefaultBraidContext(dataLoaderRegistry)));
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        verify(barQueryExecutor, times(1)).apply(argThat(matchesInput(barInput)));
        assertEquals(emptyList(), result.getErrors());

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

        @Override
        public String toString() {
            return input.toString();
        }
    }
}
