package com.atlassian.braid;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.introspection.IntrospectionQuery.INTROSPECTION_QUERY;

/**
 * Data source for an external HTTP graphql service
 */
public class HttpDataSource implements DataSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String namespace;
    private final URL remoteUrl;
    private final List<Link> links;
    private final Document schema;
    
    public HttpDataSource(String namespace, URL remoteUrl, List<Link> links) {
        this.namespace = namespace;
        this.remoteUrl = remoteUrl;
        this.links = links;

        try {
            this.schema = loadSchema();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Document getSchema() {
        return schema;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public Object query(ExecutionInput query) {
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

                mapper.writer().writeValue(connection.getOutputStream(), query);
                if (connection.getResponseCode() != 200) {
                    throw new IOException("Failed with HTTP error code: " + connection.getResponseCode());
                }
                final Map response = mapper.readerFor(Map.class).readValue(connection.getInputStream());
                return CompletableFuture.completedFuture(response);
            } finally {
                connection.disconnect();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Document loadSchema() throws IOException {
        final URLConnection urlConnection = remoteUrl.openConnection();
        if (!(urlConnection instanceof HttpURLConnection)) {
            throw new IOException("Expected an HTTP endpoint");
        }
        final HttpURLConnection connection = (HttpURLConnection) urlConnection;
        try {
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("content-type", "application/json");

            connection.getOutputStream().write(INTROSPECTION_QUERY.getBytes(Charset.defaultCharset()));
            connection.getOutputStream().close();
            if (connection.getResponseCode() != 200) {
                throw new IOException("Failed with HTTP error code: " + connection.getResponseCode());
            }

            final Map response = mapper.readerFor(Map.class).readValue(connection.getInputStream());

            //noinspection unchecked
            return new IntrospectionResultToSchema().createSchemaDefinition(response);
        } finally {
            connection.disconnect();
        }
    }
}
