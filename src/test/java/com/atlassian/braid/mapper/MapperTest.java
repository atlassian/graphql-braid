package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidObjects;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.atlassian.braid.mapper.Mappers.mapper;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class MapperTest {

    @Test
    public void copy() {
        assertThat(mapper().copy("foo")
                .apply(singletonMap("foo", "bar")))
                .containsEntry("foo", "bar");
    }

    @Test
    public void copyWithDefaultValue() {
        assertThat(mapper().copy("foo", () -> "defaultFoo")
                .apply(singletonMap("foo", "bar")))
                .containsEntry("foo", "bar");
    }

    @Test
    public void copyAndTransform() {
        assertThat(mapper().<String, String>copy("foo", value -> value.equals("bar") ? "baz" : value)
                .apply(singletonMap("foo", "bar")))
                .containsEntry("foo", "baz");
    }

    @Test
    public void copyWithChangedKey() {
        assertThat(mapper().copy("foo", "baz")
                .apply(singletonMap("foo", "bar")))
                .containsEntry("baz", "bar");
    }

    @Test
    public void copyMissing() {
        assertThat(mapper()
                .copy("baz")
                .apply(singletonMap("foo", "bar"))).isEmpty();
    }

    @Test
    public void copyDeepValue() {
        assertThat(mapper()
                .copy("['foo']['bar']", "jim")
                .apply(singletonMap("foo", singletonMap("bar", "baz"))))
                .containsEntry("jim", "baz");
    }


    @Test(expected = MapperException.class)
    public void copyDeepValueIsMissing() {
        assertThat(mapper()
                .copy("['baz']['bar']")
                .apply(singletonMap("foo", "bar")))
                .isEmpty();
    }

    @Test
    public void put() {
        assertThat(mapper()
                .put("foo", "bar")
                .apply(emptyMap())).containsEntry("foo", "bar");
    }

    @Test
    public void list() {
        final Map<String, Object> result = mapper().list("list", mapper().copy("foo", "baz"))
                .apply(singletonMap("foo", "bar"));

        assertThat(result).containsKey("list");
        assertThat(BraidObjects.<List<Map<String, String>>>cast(result.get("list")).get(0)).containsEntry("baz", "bar");
    }

    @Test
    public void listWithTruePredicate() {
        final Map<String, Object> result = mapper()
                .list("list",
                        inout -> true,
                        mapper().copy("foo", "baz"))
                .apply(singletonMap("foo", "bar"));

        assertThat(result).containsKey("list");
        assertThat(BraidObjects.<List<Map<String, String>>>cast(result.get("list")).get(0)).containsEntry("baz", "bar");
    }

    @Test
    public void listWithFalsePredicate() {
        final Map<String, Object> result = mapper()
                .list("list",
                        inout -> false,
                        mapper().copy("foo", "baz"))
                .apply(singletonMap("foo", "bar"));

        assertThat(result).isEmpty();
    }

    @Test
    public void copyList() {
        Map<String, Object> data = singletonMap("foo", singletonList(singletonMap("bar", "baz")));
        assertThat(BraidObjects.<List<Map<String, String>>>cast(mapper()
                .copyList("foo", "foz", mapper().copy("bar", "boz"))
                .apply(data)
                .get("foz")))
                .contains(singletonMap("boz", "baz"));
    }

    @Test
    public void copyEmbeddedList() {
        Map<String, Object> data = singletonMap("foo", singletonList(singletonMap("embeddedlist", singletonList(singletonMap("bar", "baz")))));
        assertThat(BraidObjects.<List<Map<String, List<Map<String, String>>>>>cast(mapper()
                .copyList("foo", "foz", mapper().copyList("embeddedlist", "embedded", mapper().copy("bar", "boz")))
                .apply(data).get("foz"))).contains(singletonMap("embedded", singletonList(singletonMap("boz", "baz"))));
    }

    @Test
    public void mapAndPut() {
        assertThat(BraidObjects.<Map<String, String>>cast(mapper()
                .map("foo", mapper().put("bar", "baz"))
                .apply(emptyMap()).get("foo")))
                .containsEntry("bar", "baz");
    }

    @Test
    public void mapAndPutWithTruePredicate() {
        assertThat(BraidObjects.<Map<String, String>>cast(mapper()
                .map("foo", __ -> true, mapper().put("bar", "baz"))
                .apply(emptyMap()).get("foo")))
                .containsEntry("bar", "baz");
    }

    @Test
    public void mapAndPutWithFalsePredicate() {
        assertThat(mapper()
                .map("foo", __ -> false, mapper().put("bar", "baz"))
                .apply(emptyMap()))
                .isEmpty();
    }

    @Test
    public void copyMap() {
        assertThat(mapper()
                .copyMap("foo", "faz", mapper().copy("bar", "barn"))
                .apply(singletonMap("foo", singletonMap("bar", "baz"))))
                .isEqualTo(singletonMap("faz", singletonMap("barn", "baz")));
    }
}