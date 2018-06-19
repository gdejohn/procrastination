package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PairTest {
    @Test
    void eagerPair() {
        Pair.of("foo", "bar").forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
    }
}
