package com.atlassian.braid;

import com.atlassian.braid.source.HttpGraphQLRemoteRetriever;
import com.atlassian.braid.source.GraphQLRemoteSchemaSource;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.language.SourceLocation;
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


@SuppressWarnings("unchecked")
public class RemoteHttpGraphQLSchemaSourceTest {

    @Test
    public void testSuccess() throws IOException, ExecutionException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(read("/com/atlassian/braid/http-success.json")));
        server.start();

        GraphQLRemoteSchemaSource source = new GraphQLRemoteSchemaSource<Object>(SchemaNamespace.of("bar"), this::getSchemaReader,
                new HttpGraphQLRemoteRetriever(server.url("/").url()), Collections.emptyList());

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
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(read("/com/atlassian/braid/http-some-errors.json")));
        server.start();

        GraphQLRemoteSchemaSource source = new GraphQLRemoteSchemaSource(SchemaNamespace.of("bar"), this::getSchemaReader,
                new HttpGraphQLRemoteRetriever(server.url("/").url()), Collections.emptyList());

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

    private StringReader getSchemaReader() {
        try {
            return new StringReader(read("/com/atlassian/braid/http-bar-schema.graphql"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
