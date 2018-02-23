package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.collections.BraidObjects;
import com.atlassian.braid.mapper.Mapper;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.atlassian.braid.mapper.Mappers.fromYamlMap;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * Builds a YAML-defined schema source either for REST or GraphQL endpoints
 */
@SuppressWarnings("unchecked")
public class YamlRemoteSchemaSourceFactory {

    public static <C extends BraidContext> RestRemoteSchemaSource<C> createRestSource(Reader source, RestRemoteRetriever<C> restRemoteRetriever) {
        Map<String, Object> m = (Map<String, Object>) new Yaml().load(source);

        SchemaNamespace namespace = SchemaNamespace.of((String) m.get("name"));
        Supplier<Reader> schema = () -> new StringReader((String) m.get("schema"));

        Map<String, RestRemoteSchemaSource.RootField> rootFields = ((Map<String, Map<String, Object>>) m.getOrDefault("rootFields", emptyList()))
                .entrySet().stream()
                .map(e -> {
                    String fieldName = e.getKey();
                    Map<String, Object> params = e.getValue();

                    Mapper mapping = fromYamlMap(BraidObjects.cast(params.get("responseMapping")));

                    return new RestRemoteSchemaSource.RootField(fieldName, (String) params.get("uri"), mapping);
                })
                .collect(Collectors.toMap(f -> f.name, f -> f));

        return new RestRemoteSchemaSource<>(
                namespace,
                schema,
                restRemoteRetriever,
                rootFields,
                buildLinks(m),
                buildTopLevelFields(m)
        );
    }

    public static <C extends BraidContext> SchemaSource<C> createGraphQLSource(Reader source, GraphQLRemoteRetriever<C> graphQLRemoteRetriever) {
        Map<String, Object> m = (Map<String, Object>) new Yaml().load(source);

        SchemaNamespace namespace = SchemaNamespace.of((String) m.get("name"));
        Supplier<Reader> schema = () -> new StringReader((String) m.get("schema"));

        return new GraphQLRemoteSchemaSource<>(
                namespace,
                schema,
                graphQLRemoteRetriever,
                buildLinks(m),
                buildTopLevelFields(m)
        );
    }

    private static String[] buildTopLevelFields(Map<String, Object> m) {
        return ofNullable((List<String>) m.get("topLevelFields")).orElse(emptyList()).toArray(new String[0]);
    }

    private static List<Link> buildLinks(Map<String, Object> m) {
        return ofNullable((List<Map<String, Map<String, String>>>) m.get("links"))
                .map(links -> links.stream().map(
                        l -> {
                            Link.LinkBuilder link = Link.from(
                                    SchemaNamespace.of((String) m.get("name")),
                                    l.get("from").get("type"),
                                    l.get("from").get("field"),
                                    l.get("from").getOrDefault("fromField",
                                            l.get("from").get("field")))
                                    .to(
                                            SchemaNamespace.of(l.get("to").get("namespace")),
                                            l.get("to").get("type"),
                                            l.get("to").get("field")
                                    );
                            ofNullable(l.get("to").get("argument")).ifPresent(link::argument);
                            return link.build();
                        })
                        .collect(Collectors.toList()))
                .orElse(emptyList());
    }
}
