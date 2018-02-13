package com.atlassian.braid.source;

import com.atlassian.braid.BatchLoaderFactory;
import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.ExecutionInput;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
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

import static com.atlassian.braid.TypeUtils.filterQueryType;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;

/**
 * Data source for an external REST service.
 */
@SuppressWarnings("WeakerAccess")
public class RestRemoteSchemaSource<C> implements SchemaSource<C>, BatchLoaderFactory {

    private final SchemaNamespace namespace;
    private final RestRemoteRetriever<C> remoteRetriever;
    private final Map<String, RootField> rootFields;
    private final List<Link> links;
    private final TypeDefinitionRegistry publicSchema;
    private final TypeDefinitionRegistry privateSchema;

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

    public RestRemoteSchemaSource(SchemaNamespace namespace,
                                  Supplier<Reader> schemaProvider,
                                  RestRemoteRetriever<C> remoteRetriever,
                                  Map<String, RootField> rootFields,
                                  List<Link> links,
                                  String... topLevelFields) {
        this.namespace = namespace;
        this.remoteRetriever = remoteRetriever;
        this.rootFields = rootFields;
        this.links = links;

        TypeDefinitionRegistry schema = loadSchema(schemaProvider);
        filterQueryType(schema, topLevelFields);
        this.publicSchema = schema;
        this.privateSchema = loadSchema(schemaProvider);
    }

    @Override
    public TypeDefinitionRegistry getSchema() {
        return publicSchema;
    }

    @Override
    public TypeDefinitionRegistry getPrivateSchema() {
        return privateSchema;
    }

    @Override
    public SchemaNamespace getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public CompletableFuture<DataFetcherResult<Map<String, Object>>> query(ExecutionInput query, C context) {
        throw new UnsupportedOperationException();
    }

    private TypeDefinitionRegistry loadSchema(Supplier<Reader> schema) {
        SchemaParser parser = new SchemaParser();
        return parser.parse(schema.get());
    }

    @Override
    public <C extends BraidContext> BatchLoader<DataFetchingEnvironment, DataFetcherResult<Map<String, Object>>> newBatchLoader(SchemaSource<C> schemaSource, Link link) {
        return environments -> {
            List<CompletableFuture<DataFetcherResult<Map<String, Object>>>> results = new ArrayList<>();
            for (DataFetchingEnvironment env : environments) {

                String uri;
                Function<Map<String, Object>, Map<String, Object>> mapper;
                if (link == null) {
                    RootField field = rootFields.get(env.getFieldDefinition().getName());
                    uri = replaceParams(env.getArguments(), field.uri);
                    mapper = field.mapper;

                } else {
                    Map<String, Object> source = env.getSource();
                    RootField field = rootFields.get(link.getTargetField());
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
                .reduce(template, (s, e) -> s.replace("{" + e.getKey() + "}", e.getValue().toString()),
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
