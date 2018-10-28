package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionsTest {
    @Test
    void fix() {
        Function<Integer, Integer> factorial = Functions.fix(f -> n -> n == 0 ? 1 : n * f.apply(n - 1));
        assertThat(factorial.apply(6)).isEqualTo(720);
    }
}
