package com.atlassian.braid.source.yaml;

import com.atlassian.braid.BraidContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Simple remote retriever that retrieves data using the built-in HTTP client
 */
public class HttpRestRemoteRetriever<C> implements RestRemoteRetriever<C> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public CompletableFuture<Map<String, Object>> get(URL url, BraidContext<C> context) {
        return CompletableFuture.completedFuture(queryForJson(url));
    }

    private Map<String, Object> queryForJson(URL url) {
        try {
            final URLConnection urlConnection = url.openConnection();
            if (!(urlConnection instanceof HttpURLConnection)) {
                throw new RuntimeException("Expected an HTTP endpoint");
            }
            final HttpURLConnection connection = (HttpURLConnection) urlConnection;
            try {
                connection.setDoInput(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

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
