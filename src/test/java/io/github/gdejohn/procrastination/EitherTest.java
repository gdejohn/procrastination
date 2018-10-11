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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class EitherTest {
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
    void fromCallable() {
        assertAll(
            () -> assertThat(Either.from(() -> { throw new Exception("foo"); }).leftOrThrow()).hasMessage("foo"),
            () -> assertThat(Either.from(() -> "foo").rightOrThrow()).isEqualTo("foo")
        );
    }

    @Test
    void fromCompletableFuture() {
        assertAll(
            () -> assertThat(
                Either.from(failedFuture(new Exception("foo"))).leftOrThrow()
            ).hasCause(new Exception("foo")),
            () -> assertThat(Either.from(completedFuture("foo")).rightOrThrow()).isEqualTo("foo")
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
    void leftMaybe() {
        assertAll(
            () -> assertThat(Either.left("foo").left()).containsExactly("foo"),
            () -> assertThat(Either.right("foo").left()).isEmpty()
        );
    }

    @Test
    void leftOrDefault() {
        assertAll(
            () -> assertThat(Either.left("foo").leftOr("bar")).isEqualTo("foo"),
            () -> assertThat(Either.right("foo").leftOr("bar")).isEqualTo("bar")
        );
    }

    @Test
    void leftOrDefaultLazy() {
        assertAll(
            () -> assertThat(Either.left("foo").leftOr(() -> "bar")).isEqualTo("foo"),
            () -> assertThat(Either.right("foo").leftOr(() -> "bar")).isEqualTo("bar")
        );
    }

    @Test
    void leftOrFunction() {
        assertAll(
            () -> assertThat(Either.left("foo").leftOr(right -> "bar")).isEqualTo("foo"),
            () -> assertThat(Either.right("foo").leftOr(String::length)).isEqualTo(3)
        );
    }

    @Test
    void leftOrThrow() {
        assertAll(
            () -> assertThat(Either.left("foo").leftOrThrow()).isEqualTo("foo"),
            () -> assertThatThrownBy(() -> Either.right("foo").leftOrThrow()).isInstanceOf(AssertionError.class)
        );
    }

    @Test
    void leftOrThrowCustom() {
        assertAll(
            () -> assertThat(Either.left("foo").leftOrThrow(IllegalStateException::new)).isEqualTo("foo"),
            () -> assertThatThrownBy(
                () -> Either.right("foo").leftOrThrow(() -> new Exception("foo"))
            ).hasMessage("foo")
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
    void rightMaybe() {
        assertAll(
            () -> assertThat(Either.right("foo").right()).containsExactly("foo"),
            () -> assertThat(Either.left("foo").right()).isEmpty()
        );
    }

    @Test
    void rightOrDefault() {
        assertAll(
            () -> assertThat(Either.right("foo").rightOr("bar")).isEqualTo("foo"),
            () -> assertThat(Either.left("foo").rightOr("bar")).isEqualTo("bar")
        );
    }

    @Test
    void rightOrDefaultLazy() {
        assertAll(
            () -> assertThat(Either.right("foo").rightOr(() -> "bar")).isEqualTo("foo"),
            () -> assertThat(Either.left("foo").rightOr(() -> "bar")).isEqualTo("bar")
        );
    }

    @Test
    void rightOrFunction() {
        assertAll(
            () -> assertThat(Either.right("foo").rightOr(right -> "bar")).isEqualTo("foo"),
            () -> assertThat(Either.left("foo").rightOr(String::length)).isEqualTo(3)
        );
    }

    @Test
    void rightOrThrow() {
        assertAll(
            () -> assertThat(Either.right("foo").rightOrThrow()).isEqualTo("foo"),
            () -> assertThatThrownBy(() -> Either.left("foo").rightOrThrow()).isInstanceOf(AssertionError.class)
        );
    }

    @Test
    void rightOrThrowCustom() {
        assertAll(
            () -> assertThat(Either.right("foo").rightOrThrow(IllegalStateException::new)).isEqualTo("foo"),
            () -> assertThatThrownBy(
                () -> Either.left("foo").rightOrThrow(() -> new Exception("foo"))
            ).hasMessage("foo")
        );
    }

    @Test
    void swap() {
        assertAll(
            () -> assertThat(Either.left("foo").swap().rightOr("bar")).isEqualTo("foo"),
            () -> assertThat(Either.left("foo").swap().leftOr("bar")).isEqualTo("bar"),
            () -> assertThat(Either.right("foo").swap().rightOr("bar")).isEqualTo("bar"),
            () -> assertThat(Either.right("foo").swap().leftOr("bar")).isEqualTo("foo")
        );
    }

    @Test
    void mapLeft() {
        assertThat(Either.left("foo").mapLeft(String::length).leftOr(0)).isEqualTo(3);
    }

    @Test
    void mapRight() {
        assertThat(Either.right("foo").mapRight(String::length).rightOr(0)).isEqualTo(3);
    }

    @Test
    void mapEither() {
        assertAll(
            () -> assertThat(Either.left("foo").mapEither(String::length, identity()).leftOr(0)).isEqualTo(3),
            () -> assertThat(Either.right("foo").mapEither(identity(), String::length).rightOr(0)).isEqualTo(3)
        );
    }

    @Test
    void flatMapLeft() {
        assertThat(Either.left("foo").flatMapLeft(compose(Either::left, String::length)).leftOr(0)).isEqualTo(3);
    }

    @Test
    void flatMapRight() {
        assertThat(Either.right("foo").flatMapRight(compose(Either::right, String::length)).rightOr(0)).isEqualTo(3);
    }

    @Test
    void flatMapEither() {
        assertAll(
            () -> assertThat(
                Either.left("foo").flatMapEither(
                    compose(Either::left, String::length),
                    Either::right
                ).leftOr(0)
            ).isEqualTo(3),
            () -> assertThat(
                Either.right("foo").flatMapEither(
                    Either::left,
                    compose(Either::right, String::length)
                ).rightOr(0)
            ).isEqualTo(3)
        );
    }

    @Test
    void equals() {
        assertAll(
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
}
