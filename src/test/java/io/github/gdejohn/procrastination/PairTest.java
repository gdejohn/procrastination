/*
 * Copyright 2018 Griffin DeJohn
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class PairTest {
    @Test
    void pair() {
        var pair = Pair.of("foo", "bar");
        pair.forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        Pair.map(pair, identity()).forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        pair.memoize().forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
    }

    @Test
    void pairLazyFirst() {
        var pair = Pair.of(() -> "foo", "bar");
        pair.forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        Pair.map(pair, identity()).forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        pair.memoize().forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
    }

    @Test
    void pairLazySecond() {
        var pair = Pair.of("foo", () -> "bar");
        pair.forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        Pair.map(pair, identity()).forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        pair.memoize().forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
    }

    @Test
    void pairLazyBoth() {
        var pair = Pair.of(() -> "foo", () -> "bar");
        pair.forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        Pair.map(pair, identity()).forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
        pair.memoize().forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("bar");
            }
        );
    }

    @Test
    void fromEntry() {
        assertThat(Sequence.from(Pair.from(new SimpleEntry<>("foo", 3)))).containsExactly("foo", 3);
    }

    @Test
    void duplicateLazy() {
        var pair = Pair.duplicate(List.of("foo", "bar").iterator()::next);
        pair.forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("foo");
                assertThat(second).isEqualTo("foo");
            }
        );
        pair.forBoth(
            (first, second) -> {
                assertThat(first).isEqualTo("bar");
                assertThat(second).isEqualTo("bar");
            }
        );
    }

    @Test
    void forEach() {
        Supplier<String> next = List.of("foo", "bar", "baz").iterator()::next;
        assertAll(
            () -> Pair.forEach(
                Pair.of("foo", "bar"),
                element -> assertThat(element).isEqualTo(next.get())
            ),
            () -> assertThat(next.get()).isEqualTo("baz")
        );
    }

    @Test
    void forBoth() {
        assertThatThrownBy(
            () -> Pair.of("foo", "bar").forBoth(
                (first, second) -> {
                    assertAll(
                        () -> assertThat(first).isEqualTo("foo"),
                        () -> assertThat(second).isEqualTo("bar")
                    );
                    throw new RuntimeException("baz");
                }
            )
        ).hasMessage("baz");
    }

    @Test
    void mapFirst() {
        assertThat(Sequence.from(Pair.of("foo", "bar").mapFirst(String::length))).containsExactly(3, "bar");
    }

    @Test
    void mapSecond() {
        assertThat(Sequence.from(Pair.of("foo", "bar").mapSecond(String::length))).containsExactly("foo", 3);
    }

    @Test
    void mapBoth() {
        assertThat(Sequence.from(Pair.of("foo", 3).mapBoth(String::length, n -> n * 2))).containsExactly(3, 6);
    }

    @Test
    void swap() {
        assertThat(Sequence.from(Pair.of("foo", "bar").swap())).containsExactly("bar", "foo");
    }

    @Test
    void toEntry() {
        assertThat(Pair.of("foo", "bar").entry()).isEqualTo(new SimpleEntry<>("foo", "bar"));
    }

    @Test
    void equals() {
        var pair = Pair.of("foo", 3);
        assertAll(
            () -> assertThat(pair).isEqualTo(pair),
            () -> assertThat(pair).isEqualTo(Pair.of("foo", 3)),
            () -> assertThat(pair).isNotEqualTo(Pair.of("foo", 0)),
            () -> assertThat(pair).isNotEqualTo(Pair.of("bar", 3)),
            () -> assertThat(pair).isNotEqualTo(Pair.of("bar", 0)),
            () -> assertThat(pair).isNotEqualTo(null)
        );
    }

    @Test
    void hash() {
        assertAll(
            () -> assertThat(Pair.of("foo", 3)).hasSameHashCodeAs(Sequence.of("foo", 3)),
            () -> assertThat(Pair.of("foo", 3)).hasSameHashCodeAs(List.of("foo", 3)),
            () -> assertThat(Pair.of("foo", 3)).hasSameHashCodeAs(Objects.hash("foo", 3))
        );
    }

    @Test
    void string() {
        assertThat(Pair.of("foo", 3)).hasToString("(foo, 3)");
    }
}
