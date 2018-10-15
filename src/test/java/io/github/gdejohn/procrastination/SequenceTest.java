package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static io.github.gdejohn.procrastination.Either.left;
import static io.github.gdejohn.procrastination.Either.right;
import static io.github.gdejohn.procrastination.Functions.curry;
import static io.github.gdejohn.procrastination.Predicates.compose;
import static io.github.gdejohn.procrastination.Predicates.divides;
import static io.github.gdejohn.procrastination.Predicates.gather;
import static io.github.gdejohn.procrastination.Predicates.greaterThan;
import static io.github.gdejohn.procrastination.Sequence.cons;
import static io.github.gdejohn.procrastination.Undefined.undefined;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.Collections.enumeration;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class SequenceTest {
    @Test
    void empty() {
        var empty = Sequence.empty();
        assertAll(
            () -> assertThat(empty.matchLazy((head, tail) -> false, () -> true)).isTrue(),
            () -> assertThat(empty.match((head, tail) -> false, () -> true)).isTrue()
        );
    }

    @Test
    void nonEmpty() {
        var sequence = cons("foo", Sequence.empty());
        assertAll(
            () -> assertThat(sequence.matchLazy((head, tail) -> head.get().equals("foo"), () -> false)).isTrue(),
            () -> assertThat(sequence.matchLazy((head, tail) -> tail.isEmpty(), () -> false)).isTrue(),
            () -> assertThat(sequence.match((head, tail) -> head.equals("foo"), () -> false)).isTrue(),
            () -> assertThat(sequence.match((head, tail) -> tail.isEmpty(), () -> false)).isTrue()
        );
    }

    @Test
    void singleton() {
        var sequence = Sequence.of("foo");
        assertAll(
            () -> assertThat(sequence.match((head, tail) -> head.equals("foo") && tail.isEmpty(), false)).isTrue(),
            () -> assertThat(
                sequence.matchLazy((head, tail) -> head.get().equals("foo") && tail.isEmpty(), false)
            ).isTrue()
        );
    }

    @Test
    void nullable() {
        var sequence = Sequence.nullable((Object) null);
        assertAll(
            () -> assertThat(sequence.match((head, tail) -> false, true)).isTrue(),
            () -> assertThat(sequence.matchLazy((head, tail) -> false, true)).isTrue()
        );
    }

    @Test
    void nullableLazy() {
        var sequence = Sequence.nullable(() -> null);
        assertAll(
            () -> assertThat(sequence.match((head, tail) -> false, true)).isTrue(),
            () -> assertThat(sequence.matchLazy((head, tail) -> false, true)).isTrue()
        );
    }

    @Test
    void fromArray() {
        assertAll(
            () -> assertThat(Sequence.from(new String[0])).isEmpty(),
            () -> assertThat(Sequence.from(new String[] {"foo"})).containsExactly("foo"),
            () -> assertThat(Sequence.from(new String[] {"foo"})).isNotEqualTo(cons("bar", Sequence.empty())),
            () -> assertThat(Sequence.from(new String[] {"foo"})).isNotEmpty(),
            () -> assertThat(Sequence.from(new String[] {"foo", "bar"})).containsExactly("foo", "bar"),
            () -> assertThat(Sequence.from(new String[] {"foo", "bar"})).isNotEqualTo(
                cons("bar", cons("foo", Sequence.empty()))
            ),
            () -> assertThat(Sequence.from(new String[] {"foo", "bar"})).isNotEqualTo(
                cons("foo", cons("baz", Sequence.empty()))
            ),
            () -> assertThat(Sequence.from(new String[] {"foo", "bar"})).isNotEqualTo(
                cons("qux", cons("bar", Sequence.empty()))
            ),
            () -> assertThat(Sequence.from(new String[] {"foo", "bar"})).isNotEqualTo(
                cons("foo", cons("bar", cons("baz", Sequence.empty())))
            ),
            () -> assertThat(Sequence.from(new String[] {"foo", "bar"})).isNotEqualTo(
                cons("qux", cons("foo", cons("bar", Sequence.empty())))
            )
        );
    }

    @Test
    void fromIntArray() {
        assertThat(Sequence.from(new int[] {1, 2, 3})).containsExactly(1, 2, 3);
    }

    @Test
    void fromLongArray() {
        assertThat(Sequence.from(new long[] {1, 2, 3})).containsExactly(1L, 2L, 3L);
    }

    @Test
    void fromShortArray() {
        assertThat(Sequence.from(new short[] {1, 2, 3})).containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    void fromByteArray() {
        assertThat(Sequence.from(new byte[] {1, 2, 3})).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void fromCharArray() {
        assertThat(Sequence.from(new char[] {'a', 'b', 'c'})).containsExactly('a', 'b', 'c');
    }

    @Test
    void fromBooleanArray() {
        assertThat(Sequence.from(new boolean[] {true, false})).containsExactly(true, false);
    }

    @Test
    void fromFloatArray() {
        assertThat(Sequence.from(new float[] {1, 2, 4})).containsExactly(1f, 2f, 4f);
    }

    @Test
    void fromDoubleArray() {
        assertThat(Sequence.from(new double[] {1, 2, 4})).containsExactly(1d, 2d, 4d);
    }

    @Test
    void fromIterable() {
        assertAll(
            () -> assertThat(Sequence.from(List.of())).isEmpty(),
            () -> assertThat(Sequence.from(List.of("foo"))).containsExactly("foo"),
            () -> assertThat(Sequence.from(List.of("foo"))).isNotEmpty(),
            () -> assertThat(Sequence.from(List.of("foo", "bar"))).containsExactly("foo", "bar"),
            () -> assertThat(Sequence.from(List.of("foo", "bar"))).isNotEqualTo(
                cons("bar", cons("foo", Sequence.empty()))
            ),
            () -> assertThat(Sequence.from(List.of("foo", "bar"))).isNotEqualTo(
                cons("foo", cons("baz", Sequence.empty()))
            ),
            () -> assertThat(Sequence.from(List.of("foo", "bar"))).isNotEqualTo(
                cons("qux", cons("bar", Sequence.empty()))
            ),
            () -> assertThat(Sequence.from(List.of("foo", "bar"))).isNotEqualTo(
                cons("foo", cons("bar", cons("baz", Sequence.empty())))
            ),
            () -> assertThat(Sequence.from(List.of("foo", "bar"))).isNotEqualTo(
                cons("qux", cons("foo", cons("bar", Sequence.empty())))
            )
        );
    }

    @Test
    void fromCharSequence() {
        assertAll(
            () -> assertThat(Sequence.from("")).isEmpty(),
            () -> assertThat(Sequence.from("foo")).containsExactly("f", "o", "o")
        );
    }

    private enum Empty {}

    private enum Directions {
        NORTH, SOUTH, EAST, WEST
    }

    @Test
    void fromEnum() {
        assertAll(
            () -> assertThat(Sequence.from(Empty.class)).isEmpty(),
            () -> assertThat(Sequence.from(Directions.class)).containsExactly(
                Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST
            )
        );
    }

    @Test
    void fromMap() {
        assertAll(
            () -> assertThat(Sequence.from(Map.of())).isEmpty(),
            () -> assertThat(Sequence.from(Map.of("foo", 1, "bar", 2, "baz", 3))).containsExactlyInAnyOrder(
                Pair.of("foo", 1), Pair.of("bar", 2), Pair.of("baz", 3)
            )
        );
    }

    @Test
    void fromOptional() {
        assertAll(
            () -> assertThat(Sequence.from(Optional.empty())).isEmpty(),
            () -> assertThat(Sequence.from(Optional.of("foo"))).containsExactly("foo")
        );
    }

    @Test
    void fromCallable() {
        //noinspection ConstantConditions
        assertAll(
            () -> assertThat(Sequence.from(() -> List.of().get(1))).isEmpty(),
            () -> assertThat(Sequence.from(() -> (Object) null)).isEmpty(),
            () -> assertThat(Sequence.from(() -> "foo")).containsExactly("foo")
        );
    }

    @Test
    void fromCompletableFuture() {
        assertAll(
            () -> assertThat(Sequence.from(CompletableFuture.completedFuture("foo"))).containsExactly("foo"),
            () -> assertThat(Sequence.from(CompletableFuture.failedFuture(new Exception()))).isEmpty(),
            () -> assertThat(Sequence.from(CompletableFuture.completedFuture(null))).isEmpty()
        );
    }

    @Test
    void fromPair() {
        assertThat(Sequence.from(Pair.of("foo", "bar"))).containsExactly("foo", "bar");
    }

    @Test
    void fromSpliterator() {
        var sequence = Sequence.memoize(IntStream.rangeClosed(1, 5).spliterator());
        //noinspection DuplicateExpressions
        assertAll(
            () -> assertThat(sequence).containsExactly(1, 2, 3, 4, 5),
            () -> assertThat(sequence).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void fromEnumeration() {
        var sequence = Sequence.memoize(enumeration(List.of(1, 2, 3, 4, 5)));
        //noinspection DuplicateExpressions
        assertAll(
            () -> assertThat(sequence).containsExactly(1, 2, 3, 4, 5),
            () -> assertThat(sequence).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void iterate() {
        var powers = Sequence.iterate(1, n -> n * 2);
        assertThat(powers).startsWith(1, 2, 4, 8, 16, 32, 64, 128);
    }

    @Test
    void iterateIndex() {
        var factorials = Sequence.iterate(1L, (i, n) -> i * n);
        assertThat(factorials).startsWith(1L, 1L, 2L, 6L, 24L, 120L, 720L);
    }

    @Test
    void fibonacci() {
        assertThat(Sequence.iterate(0, 1, Integer::sum)).startsWith(0, 1, 1, 2, 3, 5, 8, 13, 21, 34);
    }

    @Test
    void repeat() {
        assertThat(Sequence.repeat("foo").take(10_000)).allMatch("foo"::equals).hasSize(10_000);
    }

    @Test
    void and() {
        assertAll(
            () -> assertThat(Sequence.and(Sequence.empty())).isTrue(),
            () -> assertThat(Sequence.and(Sequence.repeat(false))).isFalse(),
            () -> assertThat(Sequence.and(Sequence.repeat(true).insert(100_000, false))).isFalse(),
            () -> assertThat(Sequence.and(Sequence.repeat(true).take(100_000))).isTrue()
        );
    }

    @Test
    void or() {
        assertAll(
            () -> assertThat(Sequence.or(Sequence.empty())).isFalse(),
            () -> assertThat(Sequence.or(Sequence.repeat(true))).isTrue(),
            () -> assertThat(Sequence.or(Sequence.repeat(false).insert(100_000, true))).isTrue(),
            () -> assertThat(Sequence.or(Sequence.repeat(false).take(100_000))).isFalse()
        );
    }

    @Test
    void sum() {
        assertAll(
            () -> assertThat(Sequence.sum(Sequence.empty())).isEqualTo(0L),
            () -> assertThat(Sequence.sum(Sequences.range(1L, 100_000L))).isEqualTo(5_000_050_000L)
        );
    }

    @Test
    void product() {
        assertAll(
            () -> assertThat(Sequence.product(Sequence.empty())).isEqualTo(1L),
            () -> assertThat(
                Sequence.product(Sequence.repeat(1L).take(100_000).insert(50_000, Sequence.of(2L, 3L, 4L, 5L)))
            ).isEqualTo(120L)
        );
    }

    @Test
    void uncons() {
        assertAll(
            () -> assertThat(Sequence.empty().uncons()).isEmpty(),
            () -> assertThat(Sequences.range(1, 5).uncons().optional()).hasValue(Pair.of(1, Sequence.of(2, 3, 4, 5)))
        );
    }

    @Test
    void toList() {
        var sequence = Sequences.range(1, 5);
        var list = sequence.list();
        list.set(2, -1);
        assertAll(
            () -> assertThat(list).containsExactly(1, 2, -1, 4, 5),
            () -> assertThat(sequence).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void toArray() {
        assertThat(Sequences.range(1, 5).array(Integer[]::new)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void hasPrefix() {
        assertAll(
            () -> assertThat(Sequences.range(1, 5).hasPrefix(Sequences.range(1, 3))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasPrefix(Sequences.range(1, 5))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasPrefix(Sequences.range(2, 4))).isFalse(),
            () -> assertThat(Sequences.range(1, 5).hasPrefix(Sequences.range(1, 6))).isFalse(),
            () -> assertThat(Sequences.range(1, 5).hasPrefix(Sequence.of(1, 3, 4))).isFalse()
        );
    }

    @Test
    void hasSuffix() {
        assertAll(
            () -> assertThat(Sequences.range(1, 5).hasSuffix(Sequences.range(3, 5))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasSuffix(Sequences.range(1, 5))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasSuffix(Sequences.range(0, 5))).isFalse(),
            () -> assertThat(Sequences.range(1, 5).hasSuffix(Sequence.of(2, 3, 5))).isFalse()
        );
    }

    @Test
    void hasInfix() {
        assertAll(
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequences.range(2, 4))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequences.range(2, 5))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequences.range(1, 4))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequences.range(1, 5))).isTrue(),
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequences.range(2, 6))).isFalse(),
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequences.range(0, 4))).isFalse(),
            () -> assertThat(Sequences.range(1, 5).hasInfix(Sequence.of(2, 4))).isFalse()
        );
    }

    @Test
    void hasSubsequence() {
        var sequence = Sequences.range(1, 9);
        assertThat(sequence.hasSubsequence(Sequence.of(1, 3, 4, 7, 8, 9))).isTrue();
        assertThat(sequence.hasSubsequence(Sequence.of(2, 3, 3))).isFalse();
        assertThat(sequence.hasSubsequence(Sequence.of(6, 8, 10))).isFalse();
    }

    @Test
    void head() {
        assertAll(
            () -> assertThat(Sequence.empty().head()).isEmpty(),
            () -> assertThat(Sequence.of("foo").head().optional()).hasValue("foo"),
            () -> assertThat(Sequence.of("foo", "bar").head().optional()).hasValue("foo"),
            () -> assertThat(Sequences.ints().head().optional()).hasValue(0)
        );
    }

    @Test
    void last() {
        assertAll(
            () -> assertThat(Sequence.empty().last()).isEmpty(),
            () -> assertThat(Sequence.of("foo").last().optional()).hasValue("foo"),
            () -> assertThat(Sequence.of("foo", "bar").last().optional()).hasValue("bar"),
            () -> assertThat(Sequences.range(1, 100_000).last().optional()).hasValue(100_000)
        );
    }

    @Test
    void only() {
        assertAll(
            () -> assertThat(Sequence.empty().only()).isEmpty(),
            () -> assertThat(Sequence.of("foo").only().optional()).hasValue("foo"),
            () -> assertThat(Sequence.of("foo", "bar").only()).isEmpty(),
            () -> assertThat(Sequence.repeat("foo").only()).isEmpty()
        );
    }

    @Test
    void string() {
        assertAll(
            () -> assertThat(Sequences.ints()).hasToString(
                "(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, ...)"
            ),
            () -> assertThat(Sequences.range(1, 30)).hasToString(
                "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30)"
            )
        );
    }

    @Test
    void length() {
        assertAll(
            () -> assertThat(Sequence.empty().length()).isEqualTo(0),
            () -> assertThat(cons("foo", Sequence.empty()).length()).isEqualTo(1),
            () -> assertThat(Sequences.range(0, 99).length()).isEqualTo(100),
            () -> assertThat(Sequence.generate(undefined()).take(100_000).length()).isEqualTo(100_000L)
        );
    }

    @Test
    void boundedLength() {
        assertAll(
            () -> assertThat(Sequences.ints().length(10_000)).isEmpty(),
            () -> assertThat(Sequences.ints().length(-1)).isEmpty(),
            () -> assertThat(Sequences.range(0, 10_000).length(100_000).optional()).hasValue(10_001L),
            () -> assertThat(Sequences.range(0, 10_000).length(Long.MAX_VALUE).optional()).hasValue(10_001L)
        );
    }

    @Test
    void longerThanLength() {
        assertAll(
            () -> assertThat(Sequence.empty().longerThan(0)).isFalse(),
            () -> assertThat(Sequence.empty().longerThan(-1)).isTrue(),
            () -> assertThat(Sequences.range(0, 4).longerThan(4)).isTrue(),
            () -> assertThat(Sequences.range(0, 4).longerThan(5)).isFalse(),
            () -> assertThat(Sequences.ints().longerThan(5)).isTrue()
        );
    }

    @Test
    void longerThanSequence() {
        var sequence = Sequence.of(1, 2, 3);
        assertAll(
            () -> assertThat(sequence.longerThan(Sequences.range(1, 2))).isTrue(),
            () -> assertThat(sequence.longerThan(Sequences.range(1, 3))).isFalse(),
            () -> assertThat(sequence.longerThan(Sequences.range(1, 4))).isFalse()
        );
    }

    @Test
    void shorterThan() {
        assertAll(
            () -> assertThat(Sequence.empty().shorterThan(0)).isFalse(),
            () -> assertThat(Sequence.empty().shorterThan(-1)).isFalse(),
            () -> assertThat(Sequences.range(0, 4).shorterThan(5)).isFalse(),
            () -> assertThat(Sequences.range(0, 4).shorterThan(6)).isTrue(),
            () -> assertThat(Sequences.range(1, 100_000).shorterThan(1_000_000)).isTrue()
        );
    }

    @Test
    void shorterThanSequence() {
        var sequence = Sequence.of(1, 2, 3);
        assertAll(
            () -> assertThat(sequence.shorterThan(Sequences.range(1, 4))).isTrue(),
            () -> assertThat(sequence.shorterThan(Sequences.range(1, 3))).isFalse(),
            () -> assertThat(sequence.shorterThan(Sequences.range(1, 2))).isFalse()
        );
    }

    @Test
    void isSingleton() {
        assertAll(
            () -> assertThat(Sequence.empty().isSingleton()).isFalse(),
            () -> assertThat(Sequence.cons("foo", Sequence.empty()).isSingleton()).isTrue(),
            () -> assertThat(Sequences.ints().isSingleton()).isFalse()
        );
    }

    @Test
    void increasing() {
        assertAll(
            () -> assertThat(Sequence.of(1, 2, 3, 4).increasing(Integer::compare)).isTrue(),
            () -> assertThat(Sequence.of(1, 2, 2, 4).increasing(Integer::compare)).isTrue(),
            () -> assertThat(Sequence.of(1, 3, 2, 4).increasing(Integer::compare)).isFalse(),
            () -> assertThat(Sequence.<Integer>empty().increasing(Integer::compare)).isTrue()
        );
    }

    @Test
    void strictlyIncreasing() {
        assertAll(
            () -> assertThat(Sequence.of(1, 2, 3, 4).strictlyIncreasing(Integer::compare)).isTrue(),
            () -> assertThat(Sequence.of(1, 2, 2, 4).strictlyIncreasing(Integer::compare)).isFalse(),
            () -> assertThat(Sequence.of(1, 3, 2, 4).strictlyIncreasing(Integer::compare)).isFalse(),
            () -> assertThat(Sequence.<Integer>empty().strictlyIncreasing(Integer::compare)).isTrue()
        );
    }

    @Test
    void decreasing() {
        assertAll(
            () -> assertThat(Sequence.of(4, 3, 2, 1).decreasing(Integer::compare)).isTrue(),
            () -> assertThat(Sequence.of(4, 3, 3, 1).decreasing(Integer::compare)).isTrue(),
            () -> assertThat(Sequence.of(4, 2, 3, 1).decreasing(Integer::compare)).isFalse(),
            () -> assertThat(Sequence.<Integer>empty().decreasing(Integer::compare)).isTrue()
        );
    }

    @Test
    void strictlyDecreasing() {
        assertAll(
            () -> assertThat(Sequence.of(4, 3, 2, 1).strictlyDecreasing(Integer::compare)).isTrue(),
            () -> assertThat(Sequence.of(4, 3, 3, 1).strictlyDecreasing(Integer::compare)).isFalse(),
            () -> assertThat(Sequence.of(4, 2, 3, 1).strictlyDecreasing(Integer::compare)).isFalse(),
            () -> assertThat(Sequence.<Integer>empty().strictlyDecreasing(Integer::compare)).isTrue()
        );
    }

    @Test
    void pairwiseDistinct() {
        assertAll(
            () -> assertThat(Sequences.range(0, 100_000).pairwiseDistinct()).isTrue(),
            () -> assertThat(Sequences.longs().insert(50_000, 0L).pairwiseDistinct()).isFalse()
        );
    }

    @Test
    void pairwiseDistinctFunction() {
        assertAll(
            () -> assertThat(
                Sequence.of("foo", "bar", "BAR", "Foo").pairwiseDistinct((UnaryOperator<String>) String::toLowerCase)
            ).isFalse(),
            () -> assertThat(
                Sequence.of("foo", "bar", "BAZ", "Qux").pairwiseDistinct((UnaryOperator<String>) String::toLowerCase)
            ).isTrue()
        );
    }

    @Test
    void pairwiseDistinctComparator() {
        assertAll(
            () -> assertThat(Sequences.range(0, 100_000).pairwiseDistinct(Integer::compare)).isTrue(),
            () -> assertThat(Sequences.longs().insert(50_000, 0L).pairwiseDistinct(Long::compare)).isFalse()
        );
    }

    @Test
    void contains() {
        assertAll(
            () -> assertThat(Sequences.ints().contains((int) Short.MAX_VALUE)).isTrue(),
            () -> assertThat(Sequences.range(0, 100_000).contains(-1)).isFalse()
        );
    }

    @Test
    void containsAny() {
        assertAll(
            () -> assertThat(Sequences.range(0, 100_000).containsAny(Sequences.range(-1, -5))).isFalse(),
            () -> assertThat(Sequences.range(0, 100_000).containsAny(Sequence.of(-1, -2, 10_000))).isTrue(),
            () -> assertThat(Sequence.empty().containsAny(Sequence.empty())).isFalse(),
            () -> assertThat(Sequence.empty().containsAny(Sequence.repeat(unit()))).isFalse(),
            () -> assertThat(Sequence.repeat(unit()).containsAny(Sequence.empty())).isFalse()
        );
    }

    @Test
    void containsAll() {
        assertAll(
            () -> assertThat(Sequences.range(0, 100_000).containsAll(Sequences.range(-1, -5))).isFalse(),
            () -> assertThat(Sequences.range(0, 100_000).containsAll(Sequence.of(-1, -2, 10_000))).isFalse(),
            () -> assertThat(
                Sequences.range(0, 100_000).containsAll(Sequence.of(0, 1, 10, 100, 1_000, 10_000, 100_000))
            ).isTrue(),
            () -> assertThat(Sequence.empty().containsAll(Sequence.empty())).isTrue(),
            () -> assertThat(Sequence.empty().containsAll(Sequence.repeat(unit()))).isFalse(),
            () -> assertThat(Sequence.repeat(unit()).containsAll(Sequence.empty())).isTrue()
        );
    }

    @Test
    void element() {
        var sequence = Sequences.range(1, 100_000);
        assertAll(
            () -> assertThat(sequence.element(1_000_000)).isEmpty(),
            () -> assertThat(sequence.element(-1)).isEmpty(),
            () -> assertThat(sequence.element(90_000).optional()).hasValue(90_001)
        );
    }

    @Test
    void maximum() {
        assertAll(
            () -> assertThat(Sequences.maximum(Sequence.<Integer>empty())).isEmpty(),
            () -> assertThat(
                Sequences.maximum(Sequences.range(1, 100_000).insert(50_000, 1_000_000)).optional()
            ).hasValue(1_000_000)
        );
    }

    @Test
    void minimum() {
        assertAll(
            () -> assertThat(Sequences.minimum(Sequence.<Integer>empty())).isEmpty(),
            () -> assertThat(
                Sequences.minimum(Sequences.range(1, 100_000).insert(50_000, 0)).optional()
            ).hasValue(0)
        );
    }

    @Test
    void find() {
        var sequence = Sequences.range(1, 100_000);
        assertAll(
            () -> assertThat(sequence.find(greaterThan(1_000_000))).isEmpty(),
            () -> assertThat(sequence.find(greaterThan(90_000)).optional()).hasValue(90_001)
        );
    }

    @Test
    void indexElement() {
        var sequence = Sequences.range(1, 5);
        assertAll(
            () -> assertThat(sequence.index(8)).isEmpty(),
            () -> assertThat(sequence.index(3).optional()).hasValue(2L)
        );
    }

    @Test
    void foldRightEager() {
        assertThat(Sequences.range(1, 100).foldRight(0, Integer::sum)).isEqualTo(5_050);
    }

    @Test
    void foldRightEagerNoInitial() {
        assertThat(Sequences.range(1, 100).foldRight(Integer::sum)).containsExactly(5_050);
    }

    @Test
    void foldRight() {
        assertThat(
            Sequences.ints().replace(100_000, 0).foldRight(1, x -> x == 0 ? left(0) : right(y -> x * y))
        ).isEqualTo(0);
    }

    @Test
    void foldRightNoInitial() {
        assertThat(
            Sequences.ints().replace(100_000, 0).foldRight(x -> x == 0 ? left(0) : right(y -> x * y))
        ).containsExactly(0);
    }

    @Test
    void foldRightGuarded() {
        assertThat(
            Sequences.ints().foldRightLazy(Sequence.empty(), Sequence::cons).element(100_000).optional()
        ).hasValue(100_000);
    }

    @Test
    void collectToCollection() {
        var list = Sequences.range(1, 5).collect(ArrayList::new);
        assertAll(
            () -> assertThat(Sequence.<String>empty().<List<String>>collect(ArrayList::new)).isEmpty(),
            () -> assertThat(list).isInstanceOf(ArrayList.class),
            () -> assertThat(list).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void sort() {
        var array = new Integer[100_000];
        Arrays.setAll(array, Integer::valueOf);
        var list = Arrays.asList(array);
        Collections.shuffle(list);
        var sequence = Sequences.sort(Sequence.from(list)).memoize();
        assertAll(
            () -> assertThat(sequence).hasSize(array.length),
            () -> assertThat(Sequences.strictlyIncreasing(sequence)).isTrue(),
            () -> assertThat(sequence.head().optional()).hasValue(0),
            () -> assertThat(sequence.last().optional()).hasValue(99_999)
        );
    }

    @Test
    void reverse() {
        assertAll(
            () -> assertThat(Sequence.empty().reverse()).isEmpty(),
            () -> assertThat(Sequence.of(0).reverse()).containsExactly(0),
            () -> assertThat(Sequences.range(1, 2).reverse()).containsExactly(2, 1),
            () -> assertThat(Sequences.range(1, 5).reverse()).containsExactly(5, 4, 3, 2, 1)
        );
    }

    @Test
    void cycle() {
        assertAll(
            () -> assertThat(Sequence.empty().cycle()).isEmpty(),
            () -> assertThat(Sequence.empty().cycle()).hasSize(0),
            () -> assertThat(Sequences.range(1, 3).cycle()).startsWith(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3)
        );
    }

    @Test
    void cycleN() {
        var sequence = Sequence.empty().cycle(10_000_000);
        assertAll(
            () -> assertThat(sequence).isEmpty(),
            () -> assertThat(sequence).hasSize(0),
            () -> assertThat(Sequences.range(1, 3).cycle(0)).isEmpty(),
            () -> assertThat(Sequences.range(1, 3).cycle(3)).containsExactly(1, 2, 3, 1, 2, 3, 1, 2, 3)
        );
    }

    @Test
    void pad() {
        assertAll(
            () -> assertThat(Sequences.range(1, 4).pad(0)).startsWith(1, 2, 3, 4, 0, 0, 0, 0)
        );
    }

    @Test
    void extend() {
        assertAll(
            () -> assertThat(Sequence.empty().extend(Function.identity())).isEmpty(),
            () -> assertThat(Sequence.of(0, 1).extend(n -> n * 2)).startsWith(0, 1, 2, 4, 8, 16, 32, 64)
        );
    }

    @Test
    void append() {
        assertAll(
            () -> assertThat(Sequence.empty().append("foo")).containsExactly("foo"),
            () -> assertThat(Sequence.of("foo", "bar").append("baz")).containsExactly("foo", "bar", "baz"),
            () -> assertThat(Sequence.repeat("foo").append("bar")).startsWith("foo", "foo", "foo")
        );
    }

    @Test
    void insertSequence() {
        assertAll(
            () -> assertThat(Sequences.range(1, 4).insert(-2, Sequences.range(5, 8))).containsExactly(7, 8, 1, 2, 3, 4),
            () -> assertThat(Sequences.range(1, 4).insert(2, Sequences.range(5, 6))).containsExactly(1, 2, 5, 6, 3, 4)
        );
    }

    @Test
    void intersperse() {
        assertAll(
            () -> assertThat(Sequences.range(1, 3).intersperse(0)).containsExactly(1, 0, 2, 0, 3),
            () -> assertThat(Sequence.of(1, 2).intersperse(0)).containsExactly(1, 0, 2),
            () -> assertThat(Sequence.of(1).intersperse(0)).containsExactly(1),
            () -> assertThat(Sequence.empty().intersperse(0)).isEmpty()
        );
    }

    @Test
    void filter() {
        assertAll(
            () -> assertThat(Sequences.range(0, 10).filter(x -> x % 2 == 0)).containsExactly(0, 2, 4, 6, 8, 10),
            () -> assertThat(Sequences.range(0, 4).filter(x -> false)).isEmpty(),
            () -> assertThat(Sequences.range(0, 4).filter(x -> true)).containsExactly(0, 1, 2, 3, 4),
            () -> assertThat(Sequence.empty().filter(x -> true)).isEmpty()
        );
    }

    @Test
    void narrow() {
        assertThat(Sequence.<Number>of(1.0d, 2L, 3.0f, 4L, 5).narrow(Long.class)).containsExactly(2L, 4L);
    }

    @Test
    void take() {
        assertAll(
            () -> assertThat(Sequences.ints().take(5)).containsExactly(0, 1, 2, 3, 4),
            () -> assertThat(Sequences.ints().take(4)).containsExactly(0, 1, 2, 3),
            () -> assertThat(Sequence.of(0, 1, 2, 3).take(4)).containsExactly(0, 1, 2, 3),
            () -> assertThat(Sequence.of(0, 1, 2, 3).take(10)).containsExactly(0, 1, 2, 3),
            () -> assertThat(Sequence.empty().take(4)).isEmpty(),
            () -> assertThat(Sequences.ints().take(1)).containsExactly(0),
            () -> assertThat(Sequences.ints().take(0)).isEmpty(),
            () -> assertThat(Sequences.ints().take(-10)).isEmpty()
        );
    }

    @Test
    void skip() {
        assertAll(
            () -> assertThat(Sequences.ints().skip(5)).startsWith(5, 6, 7, 8),
            () -> assertThat(Sequences.ints().skip(4)).startsWith(4, 5, 6, 7),
            () -> assertThat(Sequences.ints().skip(1)).startsWith(1, 2, 3, 4),
            () -> assertThat(Sequences.ints().skip(0)).startsWith(0, 1, 2, 3),
            () -> assertThat(Sequences.ints().skip(-10)).startsWith(0, 1, 2, 3),
            () -> assertThat(Sequence.of(0, 1, 2, 3).skip(4)).isEmpty(),
            () -> assertThat(Sequence.of(0, 1, 2, 3).skip(10)).isEmpty(),
            () -> assertThat(Sequence.empty().skip(4)).isEmpty()
        );
    }

    @Test
    void skipWhileSequence() {
        assertThat(Sequences.ints().skipWhile(Sequence.repeat(unit()).take(5))).startsWith(5, 6, 7, 8, 9);
    }

    @Test
    void step() {
        assertAll(
            () -> assertThat(Sequence.empty().step(0)).isEmpty(),
            () -> assertThat(Sequence.empty().step(2)).isEmpty(),
            () -> assertThat(Sequences.ints().step(0)).containsExactly(0),
            () -> assertThat(Sequences.ints().step(-2)).containsExactly(0),
            () -> assertThat(Sequences.range(0, 6).step(1)).containsExactly(0, 1, 2, 3, 4, 5, 6),
            () -> assertThat(Sequences.range(0, 6).step(3)).containsExactly(0, 3, 6),
            () -> assertThat(Sequences.range(0, 7).step(3)).containsExactly(0, 3, 6),
            () -> assertThat(Sequences.range(0, 8).step(3)).containsExactly(0, 3, 6),
            () -> assertThat(Sequences.range(0, 9).step(3)).containsExactly(0, 3, 6, 9)
        );
    }

    @Test
    void slice() {
        assertThat(Sequences.range(1, 5).slice(1, 4)).containsExactly(2, 3, 4);
    }

    @Test
    void sliceLength() {
        assertThat(Sequences.range(1, 5).sliceLength(1, 3)).containsExactly(2, 3, 4);
    }

    @Test
    void delete() {
        assertAll(
            () -> assertThat(Sequence.empty().delete(0)).isEmpty(),
            () -> assertThat(Sequence.empty().delete(1)).isEmpty(),
            () -> assertThat(Sequence.empty().delete(-1)).isEmpty(),
            () -> assertThat(Sequence.of("foo").delete(0)).isEmpty(),
            () -> assertThat(Sequence.of("foo").delete(1)).containsExactly("foo"),
            () -> assertThat(Sequence.of("foo").delete(-1)).containsExactly("foo"),
            () -> assertThat(Sequences.range(1, 5).delete(2)).containsExactly(1, 2, 4, 5),
            () -> assertThat(Sequences.range(1, 5).delete(0)).containsExactly(2, 3, 4, 5),
            () -> assertThat(Sequences.range(1, 5).delete(4)).containsExactly(1, 2, 3, 4),
            () -> assertThat(Sequences.range(1, 5).delete(5)).containsExactly(1, 2, 3, 4, 5),
            () -> assertThat(Sequences.range(1, 5).delete(-1)).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void deleteRange() {
        assertAll(
            () -> assertThat(Sequence.empty().delete(0, 1)).isEmpty(),
            () -> assertThat(Sequence.empty().delete(1, 2)).isEmpty(),
            () -> assertThat(Sequence.empty().delete(-1, 0)).isEmpty(),
            () -> assertThat(Sequence.empty().delete(-2, -1)).isEmpty(),
            () -> assertThat(Sequence.empty().delete(4, 2)).isEmpty(),
            () -> assertThat(Sequence.of("foo").delete(0, 1)).isEmpty(),
            () -> assertThat(Sequence.of("foo").delete(-1, 1)).isEmpty(),
            () -> assertThat(Sequence.of("foo").delete(-1, 2)).isEmpty(),
            () -> assertThat(Sequence.of("foo").delete(1, 0)).containsExactly("foo"),
            () -> assertThat(Sequence.of("foo").delete(1, 2)).containsExactly("foo"),
            () -> assertThat(Sequence.of("foo").delete(-1, 0)).containsExactly("foo"),
            () -> assertThat(Sequence.of("foo").delete(-2, -1)).containsExactly("foo"),
            () -> assertThat(Sequences.range(1, 5).delete(0, 5)).isEmpty(),
            () -> assertThat(Sequences.range(1, 5).delete(0, 6)).isEmpty(),
            () -> assertThat(Sequences.range(1, 5).delete(-1, 5)).isEmpty(),
            () -> assertThat(Sequences.range(1, 5).delete(-1, 6)).isEmpty(),
            () -> assertThat(Sequences.range(1, 5).delete(0, 3)).containsExactly(4, 5),
            () -> assertThat(Sequences.range(1, 5).delete(3, 5)).containsExactly(1, 2, 3),
            () -> assertThat(Sequences.range(1, 5).delete(1, 4)).containsExactly(1, 5),
            () -> assertThat(Sequences.range(1, 5).delete(-1, 3)).containsExactly(4, 5),
            () -> assertThat(Sequences.range(1, 5).delete(3, 6)).containsExactly(1, 2, 3),
            () -> assertThat(Sequences.range(1, 5).delete(4, 1)).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void deleteLength() {
        assertThat(Sequences.range(1, 5).deleteLength(1, 3)).containsExactly(1, 5);
    }

    @Test
    void deleteSequence() {
        assertThat(Sequences.range(0, 8).delete(Sequence.iterate(3, n -> n + 2))).containsExactly(0, 1, 2, 4, 6, 8);
    }

    @Test
    void deduplicate() {
        assertThat(Sequence.from("mississippi").deduplicate()).containsExactly("m", "i", "s", "p");
    }

    @Test
    void deduplicateFunction() {
        assertThat(
            Sequence.of("foo", "bar", "BAR", "Foo").deduplicate((UnaryOperator<String>) String::toLowerCase)
        ).containsExactly("foo", "bar");
    }

    @Test
    void deduplicateComparator() {
        assertThat(
            Sequence.of("foo", "bar", "BAR", "Foo").deduplicate(String::compareToIgnoreCase)
        ).containsExactly("foo", "bar");
    }

    @Test
    void tail() {
        assertAll(
            () -> assertThat(Sequence.empty().tail()).isEmpty(),
            () -> assertThat(Sequence.of("foo").tail().optional()).hasValue(Sequence.empty()),
            () -> assertThat(Sequences.ints().tail().optional()).hasValueSatisfying(
                tail -> assertThat(tail).startsWith(1, 2, 3, 4, 5)
            )
        );
    }

    @Test
    void initial() {
        assertAll(
            () -> assertThat(Sequence.empty().initial()).isEmpty(),
            () -> assertThat(Sequence.of("foo").initial().optional()).hasValue(Sequence.empty()),
            () -> assertThat(Sequences.range(1, 5).initial().optional()).hasValue(Sequences.range(1, 4))
        );
    }

    @Test
    void replace() {
        assertAll(
            () -> assertThat(Sequences.range(1, 5).replace(-1, 8)).containsExactly(1, 2, 3, 4, 5),
            () -> assertThat(Sequences.range(1, 5).replace(10, 8)).containsExactly(1, 2, 3, 4, 5),
            () -> assertThat(Sequences.range(1, 5).replace(2, 8)).containsExactly(1, 2, 8, 4, 5)
        );
    }

    @Test
    void replaceSubsequence() {
        assertAll(
            () -> assertThat(Sequences.range(1, 5).replace(-2, Sequences.range(6, 9))).containsExactly(8, 9, 3, 4, 5),
            () -> assertThat(Sequences.range(1, 5).replace(2, Sequences.range(6, 9))).containsExactly(1, 2, 6, 7, 8)
        );
    }

    @Test
    void update() {
        assertAll(
            () -> assertThat(Sequences.range(1, 5).update(-1, Function.identity())).containsExactly(1, 2, 3, 4, 5),
            () -> assertThat(Sequences.range(1, 5).update(2, n -> n * 2)).containsExactly(1, 2, 6, 4, 5),
            () -> assertThat(Sequences.range(1, 5).update(8, n -> n * 2)).containsExactly(1, 2, 3, 4, 5)
        );
    }

    @Test
    void map() {
        assertAll(
            () -> assertThat(Sequences.range(0, 5).map(x -> x * 2)).containsExactly(0, 2, 4, 6, 8, 10),
            () -> assertThat(Sequence.empty().map(x -> "foo")).isEmpty()
        );
    }

    @Test
    void flatMap() {
        assertAll(
            () -> assertThat(Sequence.of("foo", "bar", "baz").flatMap(Sequence::from)).containsExactly(
                "f", "o", "o", "b", "a", "r", "b", "a", "z"
            ),
            () -> assertThat(Sequence.empty().flatMap(xs -> Sequence.of(1, 2, 3))).isEmpty()
        );
    }

    @Test
    void zip() {
        assertAll(
            () -> assertThat(Sequence.empty().zip()).isEmpty(),
            () -> assertThat(Sequence.of("foo").zip()).isEmpty(),
            () -> assertThat(Sequences.range(1, 5).zip()).containsExactly(
                Pair.of(1, 2),
                Pair.of(2, 3),
                Pair.of(3, 4),
                Pair.of(4, 5)
            )
        );
    }

    @Test
    void zipFunctions() {
        assertThat(Sequence.of("foo", "bar", "baz").zip(Sequences.ints().map(curry(Pair::of)))).containsExactly(
            Pair.of(0, "foo"),
            Pair.of(1, "bar"),
            Pair.of(2, "baz")
        );
    }

    @Test
    void index() {
        assertAll(
            () -> assertThat(Sequence.empty().index()).isEmpty(),
            () -> assertThat(Sequence.of("foo", "bar", "baz").index()).containsExactly(
                Pair.of(0L, "foo"),
                Pair.of(1L, "bar"),
                Pair.of(2L, "baz")
            )
        );
    }

    @Test
    void interleave() {
        assertAll(
            () -> assertThat(Sequence.of(0, 2, 4, 6, 8).interleave(Sequence.of(1, 3, 5, 7, 9))).containsExactly(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9
            ),
            () -> assertThat(Sequence.of(0, 2, 4, 6, 8).interleave(Sequence.of(1, 3, 5, 7))).containsExactly(
                0, 1, 2, 3, 4, 5, 6, 7
            ),
            () -> assertThat(Sequence.of(0, 2, 4, 6).interleave(Sequence.of(1, 3, 5, 7, 9))).containsExactly(
                0, 1, 2, 3, 4, 5, 6, 7
            )
        );
    }

    @Test
    void unzip() {
        assertThat(
            Sequence.from(Sequence.of("foo", "bar", "baz").index().unzip(Pair::first, Pair::second))
        ).containsExactly(
            Sequence.of(0L, 1L, 2L),
            Sequence.of("foo", "bar", "baz")
        );
    }

    @Test
    void scanLeft() {
        assertAll(
            () -> assertThat(Sequence.<Integer>empty().scanLeft(0, Integer::sum)).containsExactly(0),
            () -> assertThat(Sequence.of(1).scanLeft(1, Integer::sum)).containsExactly(1, 2),
            () -> assertThat(Sequences.range(1, 4).scanLeft(0, Integer::sum)).containsExactly(0, 1, 3, 6, 10)
        );
    }

    @Test
    void scanLeftNoInitial() {
        assertAll(
            () -> assertThat(Sequence.<Integer>empty().scanLeft(Integer::sum)).isEmpty(),
            () -> assertThat(Sequence.of(1).scanLeft(Integer::sum)).containsExactly(1),
            () -> assertThat(Sequences.range(1, 4).scanLeft(Integer::sum)).containsExactly(1, 3, 6, 10)
        );
    }

    @Test
    void scanRightEager() {
        assertThat(Sequences.range(5, 1).scanRight(0, Integer::sum)).containsExactly(15, 10, 6, 3, 1, 0);
    }

    @Test
    void scanRightEagerNoInitial() {
        assertThat(Sequences.range(5, 0).scanRight(Integer::sum)).containsExactly(15, 10, 6, 3, 1, 0);
    }

    @Test
    void scanRight() {
        assertAll(
            () -> assertThat(Sequence.<Integer>empty().scanRight(1, n -> right(x -> x + n))).containsExactly(1),
            () -> assertThat(
                Sequence.of(6, 5, 4, 0, 3, 2, 1).scanRight(1, n -> n == 0 ? left(0) : right(m -> n * m))
            ).containsExactly(0, 0, 0, 0, 6, 2, 1, 1)
        );
    }

    @Test
    void scanRightNoInitial() {
        assertAll(
            () -> assertThat(Sequence.<Integer>empty().scanRight(n -> right(x -> x + n))).isEmpty(),
            () -> assertThat(
                Sequence.of(6, 5, 4, 0, 3, 2, 1).scanRight(n -> n == 0 ? left(0) : right(m -> n * m))
            ).containsExactly(0, 0, 0, 0, 6, 2, 1)
        );
    }

    @Test
    void partition() {
        assertThat(
            Sequence.from(Sequence.iterate(0, 1, Integer::sum).take(10).partition(n -> n % 2 == 0))
        ).containsExactly(
            Sequence.of(0, 2, 8, 34),
            Sequence.of(1, 1, 3, 5, 13, 21)
        );
    }

    @Test
    void partitionEithers() {
        assertThat(
            Sequence.from(Sequences.partition(Sequence.of(left(1), right(2), right(4), left(3))))
        ).containsExactly(
            Sequence.of(1, 3),
            Sequence.of(2, 4)
        );
    }

    @Test
    void spanLength() {
        assertAll(
            () -> assertThat(Sequence.from(Sequence.empty().span(-1))).containsExactly(
                Sequence.empty(),
                Sequence.empty()
            ),
            () -> assertThat(Sequence.from(Sequence.empty().span(0))).containsExactly(
                Sequence.empty(),
                Sequence.empty()
            ),
            () -> assertThat(Sequence.from(Sequence.empty().span(1))).containsExactly(
                Sequence.empty(),
                Sequence.empty()
            ),
            () -> assertThat(Sequence.from(Sequences.range(0, 9).span(5))).containsExactly(
                Sequence.of(0, 1, 2, 3, 4),
                Sequence.of(5, 6, 7, 8, 9)
            ),
            () -> assertThat(Sequence.from(Sequences.range(0, 9).span(0))).containsExactly(
                Sequence.empty(),
                Sequence.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            ),
            () -> assertThat(Sequence.from(Sequences.range(0, 9).span(10))).containsExactly(
                Sequence.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                Sequence.empty()
            ),
            () -> assertThat(Sequence.from(Sequences.range(0, 9).span(15))).containsExactly(
                Sequence.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                Sequence.empty()
            ),
            () -> assertThat(Sequence.from(Sequences.range(0, 9).span(-5))).containsExactly(
                Sequence.empty(),
                Sequence.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            )
        );
    }

    @Test
    void slide() {
        assertThat(Sequences.range(1, 5).slide(3)).containsExactly(
            Sequence.of(1, 2, 3),
            Sequence.of(2, 3, 4),
            Sequence.of(3, 4, 5),
            Sequence.of(4, 5),
            Sequence.of(5)
        );
    }

    @Test
    void groupLength() {
        assertAll(
            () -> assertThat(Sequence.empty().group(3)).isEmpty(),
            () -> assertThat(Sequences.range(0, 10).group(3)).containsExactly(
                Sequence.of(0, 1, 2),
                Sequence.of(3, 4, 5),
                Sequence.of(6, 7, 8),
                Sequence.of(9, 10)
            )
        );
    }

    @Test
    void group() {
        assertThat(Sequence.from("mississippi").group()).containsExactly(
            Sequence.of("m"),
            Sequence.of("i"),
            Sequence.from("ss"),
            Sequence.of("i"),
            Sequence.from("ss"),
            Sequence.of("i"),
            Sequence.from("pp"),
            Sequence.of("i")
        );
    }

    @Test
    void groupFunction() {
        assertAll(
            () -> assertThat(Sequence.empty().group(Object::hashCode)).isEmpty(),
            () -> assertThat(Sequence.of("foo").group(Object::hashCode)).containsExactly(Sequence.of("foo")),
            () -> assertThat(Sequence.of(8, 3, 15, 20, 5, 17, 29).group(n -> n % 12)).containsExactly(
                Sequence.of(8),
                Sequence.of(3, 15),
                Sequence.of(20),
                Sequence.of(5, 17, 29)
            )
        );
    }

    @Test
    void prefixes() {
        assertThat(Sequences.range(1, 4).prefixes()).containsExactly(
            Sequence.empty(),
            Sequence.of(1),
            Sequence.of(1, 2),
            Sequence.of(1, 2, 3),
            Sequence.of(1, 2, 3, 4)
        );
    }

    @Test
    void suffixes() {
        assertThat(Sequences.range(1, 4).suffixes()).containsExactly(
            Sequence.of(1, 2, 3, 4),
            Sequence.of(2, 3, 4),
            Sequence.of(3, 4),
            Sequence.of(4),
            Sequence.empty()
        );
    }

    @Test
    void infixes() {
        assertThat(Sequences.range(1, 4).infixes()).containsExactlyInAnyOrder(
            Sequence.empty(),
            Sequence.of(1),
            Sequence.of(2),
            Sequence.of(3),
            Sequence.of(4),
            Sequence.of(1, 2),
            Sequence.of(2, 3),
            Sequence.of(3, 4),
            Sequence.of(1, 2, 3),
            Sequence.of(2, 3, 4),
            Sequence.of(1, 2, 3, 4)
        );
    }

    @Test
    void subsequences() {
        assertThat(Sequences.range(1, 4).subsequences()).containsExactlyInAnyOrder(
            Sequence.empty(),
            Sequence.of(1),
            Sequence.of(2),
            Sequence.of(3),
            Sequence.of(4),
            Sequence.of(1, 2),
            Sequence.of(1, 3),
            Sequence.of(1, 4),
            Sequence.of(2, 3),
            Sequence.of(2, 4),
            Sequence.of(3, 4),
            Sequence.of(1, 2, 3),
            Sequence.of(1, 2, 4),
            Sequence.of(1, 3, 4),
            Sequence.of(2, 3, 4),
            Sequence.of(1, 2, 3, 4)
        );
    }

    @Test
    void sequences() {
        assertAll(
            () -> assertThat(Sequence.from("xy").sequences()).startsWith(
                Sequence.empty(),
                Sequence.from("x"),
                Sequence.from("y"),
                Sequence.from("xx"),
                Sequence.from("xy"),
                Sequence.from("yx"),
                Sequence.from("yy")
            ),
            () -> assertThat(Sequence.empty().sequences()).containsExactly(Sequence.empty())
        );
    }

    @Test
    void permutations() {
        assertThat(Sequences.range(1, 3).permutations()).containsExactlyInAnyOrder(
            Sequence.of(1, 2, 3),
            Sequence.of(1, 3, 2),
            Sequence.of(2, 1, 3),
            Sequence.of(2, 3, 1),
            Sequence.of(3, 1, 2),
            Sequence.of(3, 2, 1)
        );
    }

    @Test
    void combinations() {
        var n = 5;
        var k = 3;
        var fiveChooseThree = 10;
        var sequence = Sequences.range(1, n);
        var combinations = sequence.combinations(k);
        assertAll(
            () -> assertThat(combinations).hasSize(fiveChooseThree),
            () -> assertThat(combinations).doesNotHaveDuplicates(),
            () -> assertThat(combinations).allSatisfy(
                combination -> assertAll(
                    () -> assertThat(combination).hasSize(k),
                    () -> assertThat(combination).doesNotHaveDuplicates(),
                    () -> assertThat(sequence).containsSubsequence(combination)
                )
            ),
            () -> assertThat(sequence.combinations(5)).containsExactly(sequence),
            () -> assertThat(sequence.combinations(6)).isEmpty(),
            () -> assertThat(sequence.combinations(1)).containsExactly(
                Sequence.of(1),
                Sequence.of(2),
                Sequence.of(3),
                Sequence.of(4),
                Sequence.of(5)
            ),
            () -> assertThat(Sequences.ints().combinations(0)).containsExactly(Sequence.empty()),
            () -> assertThat(Sequences.ints().combinations(-1)).isEmpty()
        );
    }

    @Test
    void partitions() {
        assertThat(
            Sequences.range(1, 5).partitions().map(Functions.compose(Sequences::sort, Sequences::concatenate))
        ).allMatch(Sequences.range(1, 5)::equals);
    }

    @Test
    void fizzBuzz() {
        assertThat(
            Sequences.ints().map(
                i -> Sequence.of(
                    Pair.of(3, "Fizz"),
                    Pair.of(5, "Buzz")
                ).filter(
                    compose(divides(i), Pair::first)
                ).map(
                    Pair::second
                ).foldLeft(
                    String::concat
                ).or(i::toString)
            ).element(90).optional()
        ).hasValue("FizzBuzz");
    }

    private static long gcd(long a, long b) {
        if (a > b) {
            return gcd(a - b, b);
        } else if (a < b) {
            return gcd(a, b - a);
        } else {
            return a;
        }
    }

    @Test
    void rationals() {
        assertThat(
            Sequences.rationals().take(100_000)
        ).startsWith(
            Pair.of(1L, 1L),
            Pair.of(1L, 2L),
            Pair.of(2L, 1L),
            Pair.of(1L, 3L),
            Pair.of(3L, 2L),
            Pair.of(2L, 3L),
            Pair.of(3L, 1L),
            Pair.of(1L, 4L),
            Pair.of(4L, 3L),
            Pair.of(3L, 5L),
            Pair.of(5L, 2L),
            Pair.of(2L, 5L),
            Pair.of(5L, 3L),
            Pair.of(3L, 4L),
            Pair.of(4L, 1L)
        ).allMatch(
            gather((p, q) -> p > 0 && q > 0 && gcd(p, q) == 1)
        ).doesNotHaveDuplicates();
    }

    @Test
    void lexicographic() {
        var comparator = Sequences.<Integer>lexicographically();
        assertAll(
            () -> assertThat(comparator.compare(Sequences.range(0, 10_000), Sequences.range(0, 10_000))).isEqualTo(0),
            () -> assertThat(comparator.compare(Sequences.range(0, 10_000), Sequences.range(0, 10_001))).isLessThan(0),
            () -> assertThat(comparator.compare(Sequences.range(0, 10_001), Sequences.range(0, 10_000))).isGreaterThan(0)
        );
    }

    @Test
    void sequenceFuture() {
        assertThat(
            Sequences.future(
                supplyAsync(() -> "foo", delayedExecutor(25, MILLISECONDS)),
                supplyAsync(() -> "bar", delayedExecutor(75, MILLISECONDS)),
                supplyAsync(() -> "baz", delayedExecutor(50, MILLISECONDS))
            ).join()
        ).containsExactly("foo", "bar", "baz");
    }

    @Test
    void sequenceMaybe() {
        assertThat(Sequences.maybe(Sequences.ints().map(Maybe::of).insert(100_000, Maybe.empty()))).isEmpty();
    }

    @Test
    void sequenceRight() {
        assertThat(
            Sequences.right(Sequences.ints().map(Either::right).insert(100_000, Either.left("foo"))).leftOr("bar")
        ).isEqualTo("foo");
    }

    @Test
    void sequenceLeft() {
        assertThat(
            Sequences.left(Sequences.ints().map(Either::left).insert(100_000, Either.right("foo"))).rightOr("bar")
        ).isEqualTo("foo");
    }

    @Test
    void zipN() {
        assertThat(Sequences.zip(Sequence.empty())).isEqualTo(Sequence.of(Sequence.empty()));
        assertThat(Sequences.zip(Sequence.of(Sequence.empty()))).isEmpty();
        assertThat(Sequences.zip(Sequence.of(Sequences.range(0, 4)))).isEqualTo(Sequences.range(0, 4).map(Sequence::of));
    }
}
