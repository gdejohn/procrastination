package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EitherTest {
    @Test
    void left() {
        var either = Either.left("foo");
        assertThat(either.match("foo"::equals, Functions.constant(false))).isTrue();
    }
}
