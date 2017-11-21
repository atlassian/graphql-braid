package com.atlassian.braid;

import com.atlassian.braid.source.HttpRemoteRetriever;
import com.atlassian.braid.source.RemoteSchemaSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.introspection.IntrospectionQuery;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.atlassian.braid.Util.read;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class RemoteHttpSchemaSourceTest {

    @Test
    public void testSuccess() throws IOException, ExecutionException, InterruptedException {
        GraphQL build = getGraphQL();

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(new ObjectMapper().writeValueAsString(build.execute(IntrospectionQuery.INTROSPECTION_QUERY).toSpecification())));
        server.enqueue(new MockResponse().setBody(read("/com/atlassian/braid/http-success.json")));
        server.start();

        RemoteSchemaSource source = new RemoteSchemaSource<Object>(SchemaNamespace.of("bar"), new HttpRemoteRetriever(server.url("/").url()), Collections.emptyList());

        DataFetcherResult<Map> result = (DataFetcherResult<Map>) source.query(
                new ExecutionInput("blah", "op", null, null, Collections.emptyMap()), null).get();

        assertThat(result.getErrors()).isEmpty();

        Map<String, Object> bar = (Map<String, Object>) result.getData().get("bar");
        Map<String, Object> baz = (Map<String, Object>) bar.get("baz");

        assertThat(bar.get("title")).isEqualTo("Bar");
        assertThat(baz.get("rating")).isEqualTo(5);
    }

    @Test
    public void testSomeErrors() throws IOException, ExecutionException, InterruptedException {
        GraphQL build = getGraphQL();

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(new ObjectMapper().writeValueAsString(build.execute(IntrospectionQuery.INTROSPECTION_QUERY).toSpecification())));
        server.enqueue(new MockResponse().setBody(read("/com/atlassian/braid/http-some-errors.json")));
        server.start();

        RemoteSchemaSource source = new RemoteSchemaSource(SchemaNamespace.of("bar"), new HttpRemoteRetriever(server.url("/").url()), Collections.emptyList());

        DataFetcherResult<Map> result = (DataFetcherResult<Map>) source.query(
                new ExecutionInput("blah", "op", null, null, Collections.emptyMap()), null).get();

        assertThat(result.getErrors()).isNotEmpty();
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).isEqualTo("No Baz");
        assertThat(error.getPath()).isEqualTo(singletonList("baz"));
        assertThat(error.getLocations()).isEqualTo(singletonList(new SourceLocation(2, 12)));

        Map<String, Object> bar = (Map<String, Object>) result.getData().get("bar");
        assertThat(bar.get("title")).isEqualTo("Bar");
        assertThat(bar.get("baz")).isEqualTo(null);
    }

    private GraphQL getGraphQL() throws IOException {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = parser.parse(new StringReader(read("/com/atlassian/braid/http-bar-schema.graphql")));
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
        return GraphQL.newGraphQL(graphQLSchema).build();
    }
}
