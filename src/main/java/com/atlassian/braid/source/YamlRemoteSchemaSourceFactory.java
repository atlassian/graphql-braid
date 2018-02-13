package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.mapper.YamlMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * Builds a YAML-defined schema source either for REST or GraphQL endpoints
 */
@SuppressWarnings("unchecked")
public class YamlRemoteSchemaSourceFactory {

    public static <C> RestRemoteSchemaSource<C> createRestSource(Reader source, RestRemoteRetriever<C> restRemoteRetriever) {
        Map<String, Object> m = (Map<String, Object>) new Yaml().load(source);

        SchemaNamespace namespace = SchemaNamespace.of((String) m.get("name"));
        Supplier<Reader> schema = () -> new StringReader((String) m.get("schema"));

        Map<String, RestRemoteSchemaSource.RootField> rootFields = ((Map<String, Map<String, Object>>)m.getOrDefault("rootFields", emptyList()))
                .entrySet().stream()
                .map(e -> {
                    String fieldName = e.getKey();
                    Map<String, Object> params = e.getValue();
                    YamlMapper mapping = new YamlMapper((Map<String, Object>) params.get("responseMapping"));
                    return new RestRemoteSchemaSource.RootField(fieldName, (String)params.get("uri"), res -> mapping.map(res));
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

    public static <C> SchemaSource<C> createGraphQLSource(Reader source, GraphQLRemoteRetriever<C> graphQLRemoteRetriever) {
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
