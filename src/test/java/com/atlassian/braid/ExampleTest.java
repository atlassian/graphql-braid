package com.atlassian.braid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ExampleTest {
    @Test
    public void sayHi() {
        assertThat(new Example().sayHi()).isEqualTo("hi");
    }
}
