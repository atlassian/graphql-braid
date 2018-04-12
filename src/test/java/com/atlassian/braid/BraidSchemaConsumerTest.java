package com.atlassian.braid;

import com.atlassian.braid.source.LocalQueryExecutingSchemaSource;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.DataFetcherResult;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.Reader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.Util.getResourceAsReader;
import static com.atlassian.braid.Util.parseRegistry;
import static graphql.ExecutionInput.newExecutionInput;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;


@SuppressWarnings("unchecked")
public class BraidSchemaConsumerTest {

    private static final SchemaNamespace FOO = SchemaNamespace.of("foo");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Function<ExecutionInput, Object> queryExecutor;

    @Test
    public void testBraidWithExistingTypes() {
        final TypeDefinitionRegistry existingRegistry = parseRegistry("/com/atlassian/braid/existing.graphql");
        final Supplier<Reader> fooRegistry = () -> getResourceAsReader("/com/atlassian/braid/foo.graphql");

        Braid braid = Braid.builder()
                .typeDefinitionRegistry(existingRegistry)
                .schemaSource(new LocalQueryExecutingSchemaSource(FOO, fooRegistry, queryExecutor))
                .build();

        final Braid.BraidGraphQL graphql = braid.newGraphQL();

        String query = "{ foo(id: \"fooid\") { id, name } }";

        final Object context = new Object();

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

        final ExecutionResult result = graphql.execute(newExecutionInput().query(query).context(context).build()).join();

        verify(queryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        assertEquals(emptyList(), result.getErrors());

        Map<String, Map<String, Object>> data = result.getData();
        assertEquals(data.get("foo").get("name"), "Foo");
    }

    @Test
    public void testBraidWithLocalTypesAsSchemaSource() {


        final TypeDefinitionRegistry existingRegistry = parseRegistry("/com/atlassian/braid/existing.graphql");
        final TypeDefinitionRegistry fooRegistry = parseRegistry("/com/atlassian/braid/foo.graphql");

        SchemaSource localSource = mock(SchemaSource.class, withSettings().extraInterfaces(BatchLoaderFactory.class));
        when(localSource.getNamespace()).thenReturn(FOO);
        when(localSource.getSchema()).thenReturn(fooRegistry);
        BatchLoader loader = mock(BatchLoader.class);
        when(loader.load(any())).thenReturn(CompletableFuture.completedFuture(
                singletonList(new DataFetcherResult(
                        ImmutableMap.of("id", "fooid", "name", "Foo"),
                        emptyList()))));
        when(localSource.newBatchLoader(any(), any())).thenReturn(loader);


        Braid braid = Braid.builder()
                .typeDefinitionRegistry(existingRegistry)
                .schemaSource(localSource)
                .build();

        Braid.BraidGraphQL graphql = braid.newGraphQL();

        String query = "{ foo(id: \"fooid\") { id, name } }";

        final ExecutionResult result = graphql.execute(newExecutionInput().query(query).build()).join();

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
