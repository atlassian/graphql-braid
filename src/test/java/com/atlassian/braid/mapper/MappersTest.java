package com.atlassian.braid.mapper;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static com.atlassian.braid.mapper.Mappers.inputContains;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MappersTest {

    @Test
    public void testEmptyInputContains() {
        final MapperInputOutput inout = MapperInputOutputPair.of(ImmutableMap.of(), ImmutableMap.of());
        assertThat(inputContains("key").test(inout)).isFalse();
    }

    @Test
    public void testNonEmptyInputContainsWithCorrectKey() {
        final MapperInputOutput inout = MapperInputOutputPair.of(ImmutableMap.of("key", "value"), ImmutableMap.of());
        assertThat(inputContains("key").test(inout)).isTrue();
    }

    @Test
    public void testNonEmptyInputContainsWithIncorrectKey() {
        final MapperInputOutput inout = MapperInputOutputPair.of(ImmutableMap.of("bad-key", "value"), ImmutableMap.of());
        assertThat(inputContains("key").test(inout)).isFalse();
    }

    @Test
    public void testNonEmptyInputContainsWithDeepKey() {
        final MapperInputOutput inout = MapperInputOutputPair.of(ImmutableMap.of("key", ImmutableMap.of("sub-key", "value")), ImmutableMap.of());
        assertThat(inputContains("['key']['sub-key']").test(inout)).isTrue();
    }
}