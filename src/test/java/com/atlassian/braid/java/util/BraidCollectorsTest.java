package com.atlassian.braid.java.util;

import org.junit.Test;

import java.util.stream.Stream;

import static com.atlassian.braid.java.util.BraidCollectors.SingletonCharacteristics.ALLOW_MULTIPLE_OCCURRENCES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BraidCollectorsTest {

    @Test
    public void testSingletonCollectorWithASingleElementElement() {
        final Object o = new Object();
        assertThat(Stream.of(o).collect(BraidCollectors.singleton())).isSameAs(o);
    }

    @Test
    public void testSingletonCollectorWithMoreThanOneElement() {
        assertThatThrownBy(() -> Stream.of(new Object(), new Object())
                .collect(BraidCollectors.singleton())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testSingletonCollectorWithMoreThanOneSameElementNotAllowed() {
        final Object o = new Object();
        assertThatThrownBy(() -> Stream.of(o, o)
                .collect(BraidCollectors.singleton())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testSingletonCollectorWithMoreThanOneSameElementAllowed() {
        final Object o = new Object();
        assertThat(Stream.of(o, o)
                .collect(BraidCollectors.singleton(ALLOW_MULTIPLE_OCCURRENCES))).isSameAs(o);
    }
}