package com.atlassian.braid.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Simple remote retriever that retrieves data using the built-in HTTP client
 */
public class HttpGraphQLRemoteRetriever<C> implements GraphQLRemoteRetriever<C> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URL remoteUrl;

    public HttpGraphQLRemoteRetriever(URL remoteUrl) {
        this.remoteUrl = requireNonNull(remoteUrl);
    }

    @Override
    public CompletableFuture<Map<String, Object>> queryGraphQL(ExecutionInput executionInput, C context) {
        return CompletableFuture.completedFuture(queryForJson(out -> {
            try {
                mapper.writeValue(out, executionInput);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private Map<String, Object> queryForJson(Consumer<OutputStream> bodyWriter) {
        try {
            final URLConnection urlConnection = remoteUrl.openConnection();
            if (!(urlConnection instanceof HttpURLConnection)) {
                throw new RuntimeException("Expected an HTTP endpoint");
            }
            final HttpURLConnection connection = (HttpURLConnection) urlConnection;
            try {
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("content-type", "application/json");

                bodyWriter.accept(connection.getOutputStream());
                if (connection.getResponseCode() != 200) {
                    throw new IOException("Failed with HTTP error code: " + connection.getResponseCode());
                }
                return mapper.readerFor(Map.class).readValue(connection.getInputStream());
            } finally {
                connection.disconnect();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
