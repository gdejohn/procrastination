package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MaybeTest {
    @Test
    void empty() {
        var empty = Maybe.empty();
        assertAll(
            () -> assertThat(empty.matchLazy(Functions.constant(false), true)).isTrue(),
            () -> assertThat(empty.match(Functions.constant(false), true)).isTrue()
        );
    }

    @Test
    void flatMap() {
        assertThat(Maybe.of("foo").flatMap(s -> Maybe.when(!s.isEmpty(), s + "bar")).optional()).hasValue("foobar");
    }
}
