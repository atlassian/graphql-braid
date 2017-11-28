package com.atlassian.braid;

import com.atlassian.braid.source.LocalSchemaSource;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoaderRegistry;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.Map;
import java.util.function.Function;

import static com.atlassian.braid.Util.parseRegistry;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class SchemaBraidConsumerTest {

    private static final SchemaNamespace FOO = SchemaNamespace.of("foo");

    @Test
    public void testBraidWithExistingTypes() {
        Function<ExecutionInput, Object> fooQueryExecutor = mock(Function.class);
        ExecutionInput fooInput = ExecutionInput.newExecutionInput()
                .query("query Bulk_Foo {\n" +
                        "    foo100: foo(id: \"fooid\") {\n" +
                        "        id\n" +
                        "        name\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Bulk_Foo")
                .build();
        when(fooQueryExecutor.apply(argThat(matchesInput(fooInput)))).thenReturn(
                singletonMap("foo100", ImmutableMap.of("id", "fooid", "name", "Foo"))
        );

        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/existing.graphql");
        SchemaBraid schemaBraid = new SchemaBraid();
        Braid braid = schemaBraid.braid(registry, RuntimeWiring.newRuntimeWiring(),
                new LocalSchemaSource(FOO, parseRegistry("/com/atlassian/braid/foo.graphql"), fooQueryExecutor));
        DataLoaderRegistry dataLoaderRegistry = braid.newDataLoaderRegistry();

        GraphQL graphql = new GraphQL.Builder(braid.getSchema())
                .instrumentation(new DataLoaderDispatcherInstrumentation(dataLoaderRegistry))
                .build();
        String query = "{ foo(id: \"fooid\") { id, name } }";
        ExecutionResult result = graphql.execute(ExecutionInput.newExecutionInput()
                .query(query)
                .context(new DefaultBraidContext(dataLoaderRegistry, emptyMap(), query)));
        Map<String, Map<String, Object>> data = result.getData();

        verify(fooQueryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        assertEquals(emptyList(), result.getErrors());

        assertEquals(data.get("foo").get("name"), "Foo");
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
