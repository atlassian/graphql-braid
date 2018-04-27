package com.atlassian.braid.source.yaml;

import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.source.AbstractSchemaSource;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.atlassian.braid.source.SchemaUtils.loadPublicSchema;
import static com.atlassian.braid.source.SchemaUtils.loadSchema;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;

/**
 * Data source for an external REST service.
 *
 * @param <C> The underlying context for the request, wrapped in a {@link com.atlassian.braid.BraidContext}
 */
@SuppressWarnings("WeakerAccess")
public final class RestRemoteSchemaSource<C> extends AbstractSchemaSource {

    private final RestRemoteRetriever<C> remoteRetriever;
    private final Map<String, RootField> rootFields;

    /**
     * A RootField is an entity that is fetched from an invocation to the remoteRetriever. It is looked up
     * by name from the query and parameters from the environment are templated into the uri using a {} parameter
     * format.
     * <p>
     * The response is converted by the mapper to allow manipulation of fields for example renaming after retrieval.
     */
    public static final class RootField {
        String name;
        String uri;
        Function<Map<String, Object>, Map<String, Object>> mapper;

        public RootField(String name, String uri, Function<Map<String, Object>, Map<String, Object>> mapper) {
            this.name = name;
            this.uri = uri;
            this.mapper = mapper;
        }
    }

    /**
     * Create a new RestRemoteSchemaSource
     *
     * @param namespace       The namespace used to distinguish this source from others
     * @param schemaProvider  Where to read the graphql definition from
     * @param remoteRetriever Retriever that will perform the remote retrieval
     * @param rootFields      The root fields that can be populated via a call to the remoteRetriever. These fields should
     *                        all be topLevelFields if that configuration option is being used
     * @param links           links for the schema
     * @param topLevelFields  The fields to allow querying of, if not provided then all fields are queryable.
     */
    public RestRemoteSchemaSource(SchemaNamespace namespace,
                                  Supplier<Reader> schemaProvider,
                                  RestRemoteRetriever<C> remoteRetriever,
                                  Map<String, RootField> rootFields,
                                  List<Link> links,
                                  String... topLevelFields) {
        super(namespace, loadPublicSchema(schemaProvider, topLevelFields), loadSchema(schemaProvider), links);
        this.remoteRetriever = requireNonNull(remoteRetriever);
        this.rootFields = requireNonNull(rootFields);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource, Link link) {
        return environments -> {
            List<CompletableFuture<DataFetcherResult<Object>>> results = new ArrayList<>();
            for (DataFetchingEnvironment env : environments) {

                String uri;
                Function<Map<String, Object>, Map<String, Object>> mapper;
                if (link == null) {
                    RootField field = rootFields.get(env.getFieldDefinition().getName());
                    uri = replaceParams(env.getArguments(), field.uri);
                    mapper = field.mapper;

                } else {
                    Map<String, Object> source = env.getSource();
                    RootField field = rootFields.get(link.getTargetQueryField());
                    String id = (String) source.get(link.getSourceFromField());
                    uri = replaceParams(singletonMap(link.getArgumentName(), id), field.uri);
                    mapper = field.mapper;
                }

                URL url;
                try {
                    url = new URL(uri);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
                results.add(remoteRetriever.get(url, env.getContext())
                        .thenApply(mapper)
                        .thenApply(response -> new DataFetcherResult<>(response, emptyList())));
            }
            return allOf(results);
        };
    }

    public static String replaceParams(Map<String, Object> hashMap, String template) {
        return hashMap.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .reduce(template,
                        (s, e) -> s.replace("{" + e.getKey() + "}", e.getValue().toString()),
                        (s, s2) -> s);
    }

    public <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futuresList) {
        CompletableFuture<Void> allFuturesResult =
                CompletableFuture.allOf(futuresList.toArray(new CompletableFuture[0]));
        return allFuturesResult.thenApply(v ->
                futuresList.stream().
                        map(CompletableFuture::join).
                        collect(Collectors.toList())
        );
    }
}
