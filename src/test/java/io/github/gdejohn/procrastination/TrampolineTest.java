package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.github.gdejohn.procrastination.Functions.apply;
import static io.github.gdejohn.procrastination.Trampoline.call;
import static io.github.gdejohn.procrastination.Trampoline.terminate;
import static org.assertj.core.api.Assertions.assertThat;

class TrampolineTest {
    @Test
    void sequenceNthElementRecursive() {
        Optional<Integer> value = Trampoline.evaluate(
            Sequences.ints(), 100_000, f -> sequence -> index -> sequence.match(
                (head, tail) -> index == 0 ? terminate(Optional.of(head)) : call(() -> apply(f, tail, index - 1)),
                () -> terminate(Optional.empty())
            )
        );
        assertThat(value).hasValue(100_000);
    }

    @Test
    void factorial() {
        int result = Trampoline.evaluate(
            6, 1, factorial -> n -> m -> n == 0 ? terminate(m) : call(factorial, n - 1, n * m)
        );
        assertThat(result).isEqualTo(720);
    }
}
