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

import java.util.function.Supplier;

import static io.github.gdejohn.procrastination.Functions.compose;
import static io.github.gdejohn.procrastination.Functions.constant;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class EitherTest {
    @Test
    void right() {
        var either = Either.right("foo");
        assertAll(
            () -> assertThat(either.match(constant(false), constant(true))).isTrue(),
            () -> assertThat(either.match(constant(false), "foo"::equals)).isTrue(),
            () -> assertThat(either.matchLazy(constant(false), constant(true))).isTrue(),
            () -> assertThat(either.matchLazy(constant(false), compose("foo"::equals, Supplier::get))).isTrue()
        );
    }

    @Test
    void left() {
        var either = Either.left("foo");
        assertAll(
            () -> assertThat(either.match(constant(true), constant(false))).isTrue(),
            () -> assertThat(either.match("foo"::equals, constant(false))).isTrue(),
            () -> assertThat(either.matchLazy(constant(true), constant(false))).isTrue(),
            () -> assertThat(either.matchLazy(compose("foo"::equals, Supplier::get), constant(false))).isTrue()
        );
    }

    @Test
    void fromCallable() {
        assertAll(
            () -> assertThat(Either.from(() -> { throw new Exception("foo"); }).left().orThrow()).hasMessage("foo"),
            () -> assertThat(Either.from(() -> "foo").right().or("bar")).isEqualTo("foo")
        );
    }

    @Test
    void fromCompletableFuture() {
        assertAll(
            () -> assertThat(
                Either.from(failedFuture(new Exception("foo"))).left().orThrow()
            ).hasCause(new Exception("foo")),
            () -> assertThat(Either.from(completedFuture("foo")).right().or("bar")).isEqualTo("foo")
        );
    }

    @Test
    void joinRight() {
        assertAll(
            () -> assertThat(Either.joinRight(Either.right(Either.left("foo")))).isEqualTo(Either.left("foo")),
            () -> assertThat(Either.joinRight(Either.right(Either.right("bar")))).isEqualTo(Either.right("bar")),
            () -> assertThat(Either.joinRight(Either.left("bar"))).isEqualTo(Either.left("bar"))
        );
    }

    @Test
    void joinLeft() {
        assertAll(
            () -> assertThat(Either.joinLeft(Either.left(Either.left("foo")))).isEqualTo(Either.left("foo")),
            () -> assertThat(Either.joinLeft(Either.left(Either.right("bar")))).isEqualTo(Either.right("bar")),
            () -> assertThat(Either.joinLeft(Either.right("bar"))).isEqualTo(Either.right("bar"))
        );
    }

    @Test
    void join() {
        assertAll(
            () -> assertThat(Either.join(Either.left(Either.left(unit())))).isEqualTo(Either.left(unit())),
            () -> assertThat(Either.join(Either.left(Either.right(unit())))).isEqualTo(Either.right(unit())),
            () -> assertThat(Either.join(Either.right(Either.left(unit())))).isEqualTo(Either.left(unit())),
            () -> assertThat(Either.join(Either.right(Either.right(unit())))).isEqualTo(Either.right(unit()))
        );
    }

    @Test
    void merge() {
        assertAll(
            () -> assertThat(Either.merge(Either.left("foo"))).isEqualToIgnoringCase("foo"),
            () -> assertThat(Either.merge(Either.right("bar"))).isEqualToIgnoringCase("bar")
        );
    }

    @Test
    void equals() {
        var either = Either.right("foo");
        assertAll(
            () -> assertThat(either).isEqualTo(either),
            () -> assertThat(either).isNotEqualTo("foo"),
            () -> assertThat(Either.left("foo")).isEqualTo(Either.left("foo")),
            () -> assertThat(Either.left("foo")).isNotEqualTo(Either.left("bar")),
            () -> assertThat(Either.left("foo")).isNotEqualTo(Either.right("foo")),
            () -> assertThat(Either.left("foo")).isNotEqualTo(Either.right("bar")),
            () -> assertThat(Either.right("foo")).isEqualTo(Either.right("foo")),
            () -> assertThat(Either.right("foo")).isNotEqualTo(Either.right("bar")),
            () -> assertThat(Either.right("foo")).isNotEqualTo(Either.left("foo")),
            () -> assertThat(Either.right("foo")).isNotEqualTo(Either.left("bar"))
        );
    }

    @Test
    void hash() {
        assertAll(
            () -> assertThat(Either.left("foo").hashCode()).isNotEqualTo(Either.right("foo").hashCode()),
            () -> assertThat(Either.left("foo")).hasSameHashCodeAs(Pair.of("foo", unit())),
            () -> assertThat(Either.right("foo")).hasSameHashCodeAs(Pair.of(unit(), "foo"))
        );
    }

    @Test
    void string() {
        assertAll(
            () -> assertThat(Either.left("foo")).hasToString("(foo, ())"),
            () -> assertThat(Either.right("foo")).hasToString("((), foo)")
        );
    }

    @Test
    void isRight() {
        assertAll(
            () -> assertThat(Either.right("foo").isRight()).isTrue(),
            () -> assertThat(Either.left("foo").isRight()).isFalse()
        );
    }

    @Test
    void isLeft() {
        assertAll(
            () -> assertThat(Either.left("foo").isLeft()).isTrue(),
            () -> assertThat(Either.right("foo").isLeft()).isFalse()
        );
    }

    @Test
    void rightMaybe() {
        assertAll(
            () -> assertThat(Either.right("foo").right()).containsExactly("foo"),
            () -> assertThat(Either.left("foo").right()).isEmpty()
        );
    }

    @Test
    void leftMaybe() {
        assertAll(
            () -> assertThat(Either.left("foo").left()).containsExactly("foo"),
            () -> assertThat(Either.right("foo").left()).isEmpty()
        );
    }

    @Test
    void swap() {
        assertAll(
            () -> assertThat(Either.left("foo").swap().right().or("bar")).isEqualTo("foo"),
            () -> assertThat(Either.left("foo").swap().left().or("bar")).isEqualTo("bar"),
            () -> assertThat(Either.right("foo").swap().right().or("bar")).isEqualTo("bar"),
            () -> assertThat(Either.right("foo").swap().left().or("bar")).isEqualTo("foo")
        );
    }

    @Test
    void mapRight() {
        assertThat(Either.right("foo").mapRight(String::length).right().or(0)).isEqualTo(3);
    }

    @Test
    void mapLeft() {
        assertThat(Either.left("foo").mapLeft(String::length).left().or(0)).isEqualTo(3);
    }

    @Test
    void mapEither() {
        assertAll(
            () -> assertThat(Either.left("foo").mapEither(String::length, identity()).left().or(0)).isEqualTo(3),
            () -> assertThat(Either.right("foo").mapEither(identity(), String::length).right().or(0)).isEqualTo(3)
        );
    }

    @Test
    void flatMapRight() {
        assertThat(
            Either.right("foo").flatMapRight(compose(Either::right, String::length)).right().or(0)
        ).isEqualTo(3);
    }

    @Test
    void flatMapLeft() {
        assertThat(Either.left("foo").flatMapLeft(compose(Either::left, String::length)).left().or(0)).isEqualTo(3);
    }

    @Test
    void flatMapEither() {
        assertAll(
            () -> assertThat(
                Either.left("foo").flatMapEither(
                    compose(Either::left, String::length),
                    Either::right
                ).left().or(0)
            ).isEqualTo(3),
            () -> assertThat(
                Either.right("foo").flatMapEither(
                    Either::left,
                    compose(Either::right, String::length)
                ).right().or(0)
            ).isEqualTo(3)
        );
    }

    @Test
    void applyRight() {
        assertAll(
            () -> assertThat(Either.left("foo").applyRight(Either.left("bar")).left().or("baz")).isEqualTo("bar"),
            () -> assertThat(
                Either.left("foo").applyRight(Either.right(x -> unit())).left().or("baz")
            ).isEqualTo("foo"),
            () -> assertThat(Either.right("foo").applyRight(Either.left("bar")).left().or("baz")).isEqualTo("bar"),
            () -> assertThat(
                Either.right("burger").applyRight(Either.right("ham"::concat)).right().or("baz")
            ).isEqualTo("hamburger")
        );
    }

    @Test
    void applyLeft() {
        assertAll(
            () -> assertThat(Either.right("foo").applyLeft(Either.right("bar")).right().or("baz")).isEqualTo("bar"),
            () -> assertThat(
                Either.right("foo").applyLeft(Either.left(x -> unit())).right().or("baz")
            ).isEqualTo("foo"),
            () -> assertThat(Either.left("foo").applyLeft(Either.right("bar")).right().or("baz")).isEqualTo("bar"),
            () -> assertThat(
                Either.left("burger").applyLeft(Either.left("ham"::concat)).left().or("baz")
            ).isEqualTo("hamburger")
        );
    }
}
