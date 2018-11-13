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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.github.gdejohn.procrastination.Functions.compose;
import static io.github.gdejohn.procrastination.Functions.constant;
import static io.github.gdejohn.procrastination.Predicates.constant;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void nullable() {
        assertAll(
            () -> assertThat(Maybe.nullable((Object) null)).isEmpty(),
            () -> assertThat(Maybe.nullable("foo")).containsExactly("foo")
        );
    }

    @Test
    void fromOptional() {
        assertAll(
            () -> assertThat(Maybe.from(Optional.empty())).isEmpty(),
            () -> assertThat(Maybe.from(Optional.of("foo"))).containsExactly("foo")
        );
    }

    @Test
    void fromCallable() {
        assertAll(
            () -> assertThat(Maybe.from(() -> { throw new Exception(); })).isEmpty(),
            () -> assertThat(Maybe.from(() -> "foo")).containsExactly("foo")
        );
    }

    @Test
    void fromCompletableFuture() {
        assertAll(
            () -> assertThat(Maybe.from(failedFuture(new Exception()))).isEmpty(),
            () -> assertThat(Maybe.from(completedFuture("foo"))).containsExactly("foo")
        );
    }

    @Test
    void unless() {
        assertAll(
            () -> assertThat(Maybe.unless(true, "foo")).isEmpty(),
            () -> assertThat(Maybe.unless(false, "foo")).containsExactly("foo")
        );
    }

    @Test
    void unlessLazy() {
        assertAll(
            () -> assertThat(Maybe.unless(true, () -> "foo")).isEmpty(),
            () -> assertThat(Maybe.unless(false, () -> "foo")).containsExactly("foo")
        );
    }

    @Test
    void sequence() {
        assertAll(
            () -> assertThat(Maybe.empty().sequence()).isEmpty(),
            () -> assertThat(Maybe.of("foo").sequence()).containsExactly("foo"),
            () -> assertThat(Maybe.of(() -> "foo").sequence()).containsExactly("foo"),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).sequence()).containsExactly("foo")
        );
    }

    @Test
    void equals() {
        assertAll(
            () -> assertThat(Maybe.empty()).isEqualTo(Maybe.empty()),
            () -> assertThat(Maybe.empty()).isNotEqualTo(Maybe.of("bar")),
            () -> assertThat(Maybe.empty()).isNotEqualTo(Maybe.of(() -> "bar")),
            () -> assertThat(Maybe.of("foo")).isNotEqualTo(Maybe.empty()),
            () -> assertThat(Maybe.of("foo")).isNotEqualTo(Maybe.of("bar")),
            () -> assertThat(Maybe.of("foo")).isEqualTo(Maybe.of("foo")),
            () -> assertThat(Maybe.of("foo")).isEqualTo(Maybe.of(() -> "foo")),
            () -> assertThat(Maybe.of("foo")).isNotEqualTo(Maybe.of(() -> "bar")),
            () -> assertThat(Maybe.of(() -> "foo")).isNotEqualTo(Maybe.empty()),
            () -> assertThat(Maybe.of(() -> "foo")).isNotEqualTo(Maybe.of("bar")),
            () -> assertThat(Maybe.of(() -> "foo")).isEqualTo(Maybe.of("foo")),
            () -> assertThat(Maybe.of(() -> "foo")).isEqualTo(Maybe.of(() -> "foo")),
            () -> assertThat(Maybe.of(() -> "foo")).isNotEqualTo(Maybe.of(() -> "bar"))
        );
    }

    @Test
    void hash() {
        assertAll(
            () -> assertThat(Maybe.empty().hashCode()).isEqualTo(1),
            () -> assertThat(Maybe.empty()).hasSameHashCodeAs(Sequence.empty()),
            () -> assertThat(Maybe.empty()).hasSameHashCodeAs(unit()),
            () -> assertThat(Maybe.empty()).hasSameHashCodeAs(Collections.emptyList()),
            () -> assertThat(Maybe.of("foo")).hasSameHashCodeAs(Sequence.of("foo")),
            () -> assertThat(Maybe.of("foo")).hasSameHashCodeAs(Sequence.of(() -> "foo")),
            () -> assertThat(Maybe.of(() -> "foo")).hasSameHashCodeAs(Sequence.of("foo")),
            () -> assertThat(Maybe.of(() -> "foo")).hasSameHashCodeAs(Sequence.of(() -> "foo")),
            () -> assertThat(Maybe.of("foo")).hasSameHashCodeAs(List.of("foo")),
            () -> assertThat(Maybe.of(() -> "foo")).hasSameHashCodeAs(List.of("foo")),
            () -> assertThat(Maybe.of("foo").hashCode()).isEqualTo(Objects.hash("foo")),
            () -> assertThat(Maybe.of(() -> "foo").hashCode()).isEqualTo(Objects.hash("foo"))
        );
    }

    @Test
    void string() {
        assertAll(
            () -> assertThat(Maybe.empty()).hasToString("()"),
            () -> assertThat(Maybe.of("foo")).hasToString("(foo)"),
            () -> assertThat(Maybe.of(() -> "foo")).hasToString("(foo)")
        );
    }

    @Test
    void orMaybe() {
        assertAll(
            () -> assertThat(Maybe.empty().or(Maybe.empty())).isEmpty(),
            () -> assertThat(Maybe.empty().or(Maybe.of("bar"))).containsExactly("bar"),
            () -> assertThat(Maybe.of("foo").or(Maybe.empty())).containsExactly("foo"),
            () -> assertThat(Maybe.of("foo").or(Maybe.of("bar"))).containsExactly("foo"),
            () -> assertThat(Maybe.of(() -> "foo").or(Maybe.empty())).containsExactly("foo"),
            () -> assertThat(Maybe.of(() -> "foo").or(Maybe.of("bar"))).containsExactly("foo"),
            () -> assertThat(Maybe.lazy(Maybe::empty).or(Maybe.of("bar"))).containsExactly("bar"),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).or(Maybe.of("bar"))).containsExactly("foo")
        );
    }

    @Test
    void orDefault() {
        assertAll(
            () -> assertThat(Maybe.empty().or("bar")).isEqualTo("bar"),
            () -> assertThat(Maybe.of("foo").or("bar")).isEqualTo("foo")
        );
    }

    @Test
    void orDefaultLazy() {
        assertAll(
            () -> assertThat(Maybe.empty().or(() -> "bar")).isEqualTo("bar"),
            () -> assertThat(Maybe.of("foo").or(() -> "bar")).isEqualTo("foo")
        );
    }

    @Test
    void orThrow() {
        assertAll(
            () -> assertThatThrownBy(() -> Maybe.empty().orThrow()).isInstanceOf(AssertionError.class),
            () -> assertThat(Maybe.of("foo").orThrow()).isEqualTo("foo")
        );
    }

    @Test
    void orThrowCustom() {
        assertAll(
            () -> assertThatThrownBy(
                () -> Maybe.empty().orThrow(IllegalStateException::new)
            ).isInstanceOf(IllegalStateException.class),
            () -> assertThat(Maybe.of("foo").orThrow(IllegalStateException::new)).isEqualTo("foo")
        );
    }

    @Test
    void rightOrDefault() {
        assertAll(
            () -> assertThat(Maybe.empty().rightOr("foo").left().or("bar")).isEqualTo("foo"),
            () -> assertThat(Maybe.of("foo").rightOr("bar").right().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.of(() -> "foo").rightOr("bar").right().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).rightOr("bar").right().or("baz")).isEqualTo("foo")
        );
    }

    @Test
    void rightOrDefaultLazy() {
        assertAll(
            () -> assertThat(Maybe.empty().rightOr(() -> "foo").left().or("bar")).isEqualTo("foo"),
            () -> assertThat(Maybe.of("foo").rightOr(() -> "bar").right().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.of(() -> "foo").rightOr(() -> "bar").right().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).rightOr(() -> "bar").right().or("baz")).isEqualTo("foo")
        );
    }

    @Test
    void leftOrDefault() {
        assertAll(
            () -> assertThat(Maybe.empty().leftOr("foo").right().or("bar")).isEqualTo("foo"),
            () -> assertThat(Maybe.of("foo").leftOr("bar").left().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.of(() -> "foo").leftOr("bar").left().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).leftOr("bar").left().or("baz")).isEqualTo("foo")
        );
    }

    @Test
    void leftOrDefaultLazy() {
        assertAll(
            () -> assertThat(Maybe.empty().leftOr(() -> "foo").right().or("bar")).isEqualTo("foo"),
            () -> assertThat(Maybe.of("foo").leftOr(() -> "bar").left().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.of(() -> "foo").leftOr(() -> "bar").left().or("baz")).isEqualTo("foo"),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).leftOr(() -> "bar").left().or("baz")).isEqualTo("foo")
        );
    }

    @Test
    void map() {
        assertAll(
            () -> assertThat(Maybe.empty().map(constant("foo"))).isEmpty(),
            () -> assertThat(Maybe.of("foo").map(String::length)).containsExactly(3),
            () -> assertThat(Maybe.of(() -> "foo").map(String::length)).containsExactly(3),
            () -> assertThat(Maybe.lazy(() -> Maybe.of("foo")).map(String::length)).containsExactly(3)
        );
    }

    @Test
    void flatMap() {
        assertAll(
            () -> assertThat(Maybe.empty().flatMap(x -> Maybe.of("foo"))).isEmpty(),
            () -> assertThat(Maybe.of("foo").flatMap(compose(Maybe::of, String::length))).containsExactly(3),
            () -> assertThat(Maybe.of("foo").flatMap(constant(Maybe.empty()))).isEmpty(),
            () -> assertThat(Maybe.of(() -> "foo").flatMap(compose(Maybe::of, String::length))).containsExactly(3),
            () -> assertThat(
                Maybe.lazy(() -> Maybe.of("foo")).flatMap(compose(Maybe::of, String::length))
            ).containsExactly(3)
        );
    }

    @Test
    void apply() {
        assertAll(
            () -> assertThat(Maybe.empty().apply(Maybe.empty())).isEmpty(),
            () -> assertThat(Maybe.empty().apply(Maybe.of(identity()))).isEmpty(),
            () -> assertThat(Maybe.of("foo").apply(Maybe.empty())).isEmpty(),
            () -> assertThat(Maybe.of("burger").apply(Maybe.of("ham"::concat))).containsExactly("hamburger")
        );
    }

    @Test
    void filter() {
        assertAll(
            () -> assertThat(Maybe.empty().filter(constant(true))).isEmpty(),
            () -> assertThat(Maybe.of("foo").filter(constant(true))).containsExactly("foo"),
            () -> assertThat(Maybe.of(3).filter(n -> n % 2 == 0)).isEmpty()
        );
    }

    @Test
    void narrow() {
        assertAll(
            () -> assertThat(Maybe.<Number>empty().narrow(Integer.class)).isEmpty(),
            () -> assertThat(Maybe.<Number>of(0).narrow(Long.class)).isEmpty(),
            () -> assertThat(Maybe.<Number>of(0).narrow(Integer.class)).containsExactly(0)
        );
    }
}
