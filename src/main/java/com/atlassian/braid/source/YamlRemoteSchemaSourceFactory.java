package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.mapper.Mapper;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.mapper.Mappers.fromYamlList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Builds a YAML-defined schema source either for REST or GraphQL endpoints
 */
public class YamlRemoteSchemaSourceFactory {

    public static <C extends BraidContext> RestRemoteSchemaSource<C> createRestSource(Reader source, RestRemoteRetriever<C> restRemoteRetriever) {
        final Map<String, Object> m = loadYamlMap(source);

        SchemaNamespace namespace = SchemaNamespace.of((String) m.get("name"));
        Supplier<Reader> schema = () -> new StringReader((String) m.get("schema"));

        Map<String, RestRemoteSchemaSource.RootField> rootFields = BraidMaps.get(m, "rootFields")
                .map(BraidObjects::<Map<String, Map<String, Object>>>cast)
                .orElse(emptyMap())
                .entrySet().stream()
                .map(e -> {
                    String fieldName = e.getKey();
                    Map<String, Object> params = e.getValue();

                    Mapper mapping = fromYamlList(BraidObjects.cast(params.get("responseMapping")));
                    return new RestRemoteSchemaSource.RootField(fieldName, cast(params.get("uri")), mapping);
                })
                .collect(toMap(f -> f.name, f -> f));

        return new RestRemoteSchemaSource<>(
                namespace,
                schema,
                restRemoteRetriever,
                rootFields,
                buildLinks(m),
                buildTopLevelFields(m));
    }

    public static <C extends BraidContext> SchemaSource<C> createGraphQLSource(Reader source, GraphQLRemoteRetriever<C> graphQLRemoteRetriever) {
        Map<String, Object> m = loadYamlMap(source);

        SchemaNamespace namespace = SchemaNamespace.of((String) m.get("name"));
        Supplier<Reader> schema = () -> new StringReader((String) m.get("schema"));

        return new GraphQLRemoteSchemaSource<>(
                namespace,
                schema,
                graphQLRemoteRetriever,
                buildLinks(m),
                buildTopLevelFields(m));
    }

    private static Map<String, Object> loadYamlMap(Reader source) {
        return cast(new Yaml().load(source));
    }

    private static String[] buildTopLevelFields(Map<String, Object> m) {
        return BraidMaps.get(m, "topLevelFields")
                .map(BraidObjects::<List<String>>cast)
                .orElse(emptyList())
                .toArray(new String[0]);
    }

    private static List<Link> buildLinks(Map<String, Object> m) {
        final SchemaNamespace fromNamespace = SchemaNamespace.of(getOrThrow(m, "name"));

        return BraidMaps.get(m, "links")
                .map(BraidObjects::<List<Map<String, Map<String, Object>>>>cast)
                .map(links -> buildLinks(fromNamespace, links))
                .orElse(emptyList());
    }

    private static List<Link> buildLinks(SchemaNamespace fromNamespace, List<Map<String, Map<String, Object>>> links) {
        return links.stream().map(l -> buildLink(fromNamespace, l)).collect(toList());
    }

    private static Link buildLink(SchemaNamespace fromNamespace, Map<String, Map<String, Object>> linkMap) {

        final Map<String, String> from = getOrThrow(linkMap, "from");
        final Map<String, String> to = getOrThrow(linkMap, "to");

        Link.LinkBuilder linkBuilder = buildFrom(fromNamespace, from);
        linkBuilder = buildTo(linkBuilder, to);

        if (getReplaceFromField(linkMap)) {
            linkBuilder.replaceFromField();
        }

        BraidMaps.get(to, "argument").ifPresent(linkBuilder::argument);
        BraidMaps.get(to, "nullable").map(Boolean::valueOf).ifPresent(linkBuilder::setNullable);

        return linkBuilder.build();
    }

    private static Link.LinkBuilder buildFrom(SchemaNamespace fromNamespace, Map<String, String> from) {
        final String fromField = getOrThrow(from, "field");
        return Link.from(
                fromNamespace,
                getOrThrow(from, "type"),
                fromField,
                BraidMaps.get(from, "fromField").orElse(fromField));
    }

    private static Link.LinkBuilder buildTo(Link.LinkBuilder builder, Map<String, String> to) {
        return builder.to(
                SchemaNamespace.of(getOrThrow(to, "namespace")),
                getOrThrow(to, "type"),
                getOrThrow(to, "field"),
                BraidMaps.get(to, "variableField").orElse(null));
    }

    private static <T> T getOrThrow(Map<String, ?> map, String key) {
        return BraidMaps.get(map, key).map(BraidObjects::<T>cast).orElseThrow(IllegalStateException::new);
    }

    public static boolean getReplaceFromField(Map<String, Map<String, Object>> link) {
        return BraidMaps.get(link.get("from"), "replaceFromField")
                .map(BraidObjects::<Boolean>cast)
                .orElse(false);
    }
}
