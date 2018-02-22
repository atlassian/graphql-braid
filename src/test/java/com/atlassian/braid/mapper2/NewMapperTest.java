package com.atlassian.braid.mapper2;

import org.junit.Test;

import java.io.StringReader;

import static com.atlassian.braid.mapper.Mapper.newMapper;
import static com.atlassian.braid.mapper2.NewMapper.mapper;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class NewMapperTest {

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
    public void put() {
        assertThat(mapper()
                .put("foo", "bar")
                .apply(emptyMap())).containsEntry("foo", "bar");
    }
}