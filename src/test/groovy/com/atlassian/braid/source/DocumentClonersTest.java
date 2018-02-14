package com.atlassian.braid.source;

import graphql.language.Value;
import org.junit.Test;
import org.reflections.Reflections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


public class DocumentClonersTest {

    @Test
    public void testAllPossibleValueSubtypesAreSupported() {
        Reflections reflections = new Reflections(Value.class.getPackage().getName());
        for (Class subType : reflections.getSubTypesOf(Value.class)) {
            Object value = mock(subType);
            Object cloned = DocumentCloners.clone((Value) value);
            assertThat(cloned.getClass().isAssignableFrom(subType));
        }
    }
}
