package com.atlassian.braid;

import com.atlassian.braid.source.LocalSchemaSource;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoaderRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.function.Function;

import static com.atlassian.braid.Util.parseRegistry;
import static graphql.ExecutionInput.newExecutionInput;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class SchemaBraidConsumerTest {

    private static final SchemaNamespace FOO = SchemaNamespace.of("foo");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Function<ExecutionInput, Object> queryExecutor;

    @Test
    public void testBraidWithExistingTypes() throws MalformedURLException {


        final TypeDefinitionRegistry existingRegistry = parseRegistry("/com/atlassian/braid/existing.graphql");
        final TypeDefinitionRegistry fooRegistry = parseRegistry("/com/atlassian/braid/foo.graphql");

        Braid braid = new SchemaBraid<>()
                .braid(SchemaBraidConfiguration.builder()
                        .typeDefinitionRegistry(existingRegistry)
                        .runtimeWiringBuilder(newRuntimeWiring())
                        .schemaSource(new LocalSchemaSource<>(FOO, fooRegistry, queryExecutor))
                        .build());

        DataLoaderRegistry dataLoaderRegistry = braid.newDataLoaderRegistry();

        GraphQL graphql = new GraphQL.Builder(braid.getSchema())
                .instrumentation(new DataLoaderDispatcherInstrumentation(dataLoaderRegistry))
                .build();

        String query = "{ foo(id: \"fooid\") { id, name } }";

        final BraidContext context = new DefaultBraidContext(dataLoaderRegistry, emptyMap(), query);

        ExecutionInput fooInput = newExecutionInput()
                .query("query Bulk_Foo {\n" +
                        "    foo100: foo(id: \"fooid\") {\n" +
                        "        id\n" +
                        "        name\n" +
                        "    }\n" +
                        "}\n\n\n")
                .operationName("Bulk_Foo")
                .context(context)
                .build();

        when(queryExecutor.apply(argThat(matchesInput(fooInput))))
                .thenReturn(singletonMap("foo100", ImmutableMap.of("id", "fooid", "name", "Foo")));

        final ExecutionResult result = graphql.execute(newExecutionInput().query(query).context(context));

        verify(queryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        assertEquals(emptyList(), result.getErrors());

        Map<String, Map<String, Object>> data = result.getData();
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
