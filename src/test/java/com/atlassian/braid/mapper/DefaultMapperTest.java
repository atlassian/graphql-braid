package com.atlassian.braid.mapper;


import org.junit.Test;
import org.springframework.expression.spel.SpelEvaluationException;

import java.util.List;
import java.util.Map;

import static com.atlassian.braid.mapper.Mapper.newMapper;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
public class DefaultMapperTest {

    @Test
    public void copy() {
        assertThat(newMapper(singletonMap("foo", "bar"))
            .copy("foo")
            .build()).containsEntry("foo", "bar");
    }

    @Test
    public void copyWithDefaultValue() {
        assertThat(newMapper(singletonMap("foo", "bar"))
            .copy("foo", () -> "defaultFoo")
            .build()).containsEntry("foo", "bar");
    }

    @Test
    public void copyAndTransform() {
        assertThat(newMapper(singletonMap("foo", "bar"))
            .copy("foo", value -> value.equals("bar") ? "baz" : value)
            .build()).containsEntry("foo", "baz");
    }

    @Test
    public void copyWithChangedKey() {
        assertThat(newMapper(singletonMap("foo", "bar"))
                .copy("foo", "baz")
                .build()).containsEntry("baz", "bar");
    }

    @Test
    public void copyDeepValue() {
        assertThat(newMapper(singletonMap("foo", singletonMap("bar", "baz")))
                .copy("['foo']['bar']", "jim")
                .build()).containsEntry("jim", "baz");
    }

    @Test
    public void copyMissing() {
        assertThat(newMapper(singletonMap("foo", "bar"))
                .copy("baz")
                .build()).isEmpty();
    }

    @Test
    public void copyMissingWithDefaultValue() {
        assertThat(newMapper(singletonMap("foo", "bar"))
                .copy("baz", () -> "defaultBaz")
                .build()).containsEntry("baz", "defaultBaz");
    }

    @Test(expected = SpelEvaluationException.class)
    public void copyDeepValueIsMissing() {
        assertThat(newMapper(singletonMap("foo", "bar"))
                .copy("['baz']['bar']")
                .build()).isEmpty();
    }

    @Test
    public void put() {
        assertThat(newMapper(emptyMap())
                .put("foo", "bar")
                .build()).containsEntry("foo", "bar");
    }

    @Test
    public void mapAndPut() {
        assertThat((Map) newMapper(emptyMap())
                .map("foo")
                    .put("bar", "baz")
                    .done()
                .build().get("foo")).containsEntry("bar", "baz");
    }

    @Test
    public void copyMap() {
        assertThat((Map) newMapper(singletonMap("foo", singletonMap("bar", "baz")))
                .copyMap("foo", "faz")
                    .copy("bar", "barn")
                    .done()
                .build()).isEqualTo(singletonMap("faz", singletonMap("barn", "baz")));
    }

    @Test
    public void copyList() {
        Map<String, Object> data = singletonMap("foo", singletonList(singletonMap("bar", "baz")));
        assertThat((List)newMapper(data)
                .copyList("foo", "foz")
                    .copy("bar", "boz")
                    .done()
                .build().get("foz")).contains(singletonMap("boz", "baz"));
    }

    @Test
    public void copyEmbeddedList() {
        Map<String, Object> data = singletonMap("foo", singletonList(singletonMap("embeddedlist", singletonList(singletonMap("bar", "baz")))));
        assertThat((List)newMapper(data)
                .copyList("foo", "foz")
                    .copyList("embeddedlist", "embedded")
                        .copy("bar", "boz")
                        .done()
                    .done()
                .build().get("foz")).contains(singletonMap("embedded", singletonList(singletonMap("boz", "baz"))));
    }
}
