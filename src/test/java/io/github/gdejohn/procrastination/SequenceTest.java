package io.github.gdejohn.procrastination;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.github.gdejohn.procrastination.Either.left;
import static io.github.gdejohn.procrastination.Either.right;
import static io.github.gdejohn.procrastination.Predicates.compose;
import static io.github.gdejohn.procrastination.Predicates.divides;
import static io.github.gdejohn.procrastination.Predicates.gather;
import static io.github.gdejohn.procrastination.Sequence.cons;
import static io.github.gdejohn.procrastination.Unit.unit;
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
    void fibonacci() {
        assertThat(Sequence.iterate(0, 1, Integer::sum)).startsWith(0, 1, 1, 2, 3, 5, 8, 13, 21, 34);
    }

    @Test
    void repeat() {
        assertThat(Sequence.repeat("foo").take(10_000)).allMatch("foo"::equals).hasSize(10_000);
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
            () -> assertThat(Sequence.empty().head().optional()).isEmpty(),
            () -> assertThat(Sequence.of("foo").head().optional()).hasValue("foo"),
            () -> assertThat(Sequence.of("foo", "bar").head().optional()).hasValue("foo"),
            () -> assertThat(Sequences.ints().head().optional()).hasValue(0)
        );
    }

    @Test
    void last() {
        assertAll(
            () -> assertThat(Sequence.empty().last().optional()).isEmpty(),
            () -> assertThat(Sequence.of("foo").last().optional()).hasValue("foo"),
            () -> assertThat(Sequence.of("foo", "bar").last().optional()).hasValue("bar"),
            () -> assertThat(Sequences.range(1, 100_000).last().optional()).hasValue(100_000)
        );
    }

    @Test
    void only() {
        assertAll(
            () -> assertThat(Sequence.empty().only().optional()).isEmpty(),
            () -> assertThat(Sequence.of("foo").only().optional()).hasValue("foo"),
            () -> assertThat(Sequence.of("foo", "bar").only().optional()).isEmpty(),
            () -> assertThat(Sequence.repeat("foo").only().optional()).isEmpty()
        );
    }

    @Test
    void length() {
        assertAll(
            () -> assertThat(Sequence.empty().length()).isEqualTo(0),
            () -> assertThat(cons("foo", Sequence.empty()).length()).isEqualTo(1),
            () -> assertThat(Sequences.range(0, 99).length()).isEqualTo(100)
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
    void shorterThanSequence() {
        var sequence = Sequence.of(1, 2, 3);
        assertAll(
            () -> assertThat(sequence.shorterThan(Sequences.range(1, 4))).isTrue(),
            () -> assertThat(sequence.shorterThan(Sequences.range(1, 3))).isFalse(),
            () -> assertThat(sequence.shorterThan(Sequences.range(1, 2))).isFalse()
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
    void cycle() {
        var sequence = Sequence.empty().cycle(10_000_000);
        assertThat(sequence).isEmpty();
        assertThat(sequence.length()).isEqualTo(0);
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
    void foldRightEager() {
        assertThat(Sequences.range(1, 100).foldRight(0, Integer::sum)).isEqualTo(5_050);
    }

    @Test
    void foldRightLazy() {
        assertThat(
            Sequences.ints().replace(100_000, 0).foldRight(1, x -> x == 0 ? left(0) : right(y -> x * y))
        ).isEqualTo(0);
    }

    @Test
    void foldRightGuarded() {
        assertThat(
            Sequences.ints().foldRightLazy(Sequence.empty(), Sequence::cons).element(100_000).optional()
        ).hasValue(100_000);
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
    void filter() {
        assertAll(
            () -> assertThat(Sequences.range(0, 10).filter(x -> x % 2 == 0)).containsExactly(0, 2, 4, 6, 8, 10),
            () -> assertThat(Sequences.range(0, 4).filter(x -> false)).isEmpty(),
            () -> assertThat(Sequences.range(0, 4).filter(x -> true)).containsExactly(0, 1, 2, 3, 4),
            () -> assertThat(Sequence.empty().filter(x -> true)).isEmpty()
        );
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
    void deleteSequence() {
        assertThat(Sequences.range(0, 8).delete(Sequence.iterate(3, n -> n + 2))).containsExactly(0, 1, 2, 4, 6, 8);
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
    void scanLeft() {
        assertAll(
            () -> assertThat(Sequence.<Integer>empty().scanLeft(0, Integer::sum)).containsExactly(0),
            () -> assertThat(Sequence.of(1).scanLeft(1, Integer::sum)).containsExactly(1, 2),
            () -> assertThat(Sequences.range(1, 5).scanLeft(0, Integer::sum)).containsExactly(0, 1, 3, 6, 10, 15)
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
    void group() {
        assertThat(Sequence.empty().group(5)).isEmpty();
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
        assertThat(combinations).hasSize(fiveChooseThree);
        assertThat(combinations).doesNotHaveDuplicates();
        assertThat(combinations).allSatisfy(
            combination -> {
                assertThat(combination.length()).isEqualTo(k);
                assertThat(combination).doesNotHaveDuplicates();
                assertThat(sequence).containsSubsequence(combination);
            }
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
    void sequenceMaybe() {
        assertThat(Sequences.maybe(Sequences.ints().map(Maybe::of).insert(100_000, Maybe.empty()))).isEmpty();
    }

    @Test
    void sequenceRight() {
        assertThat(
            Either.merge(Sequences.right(Sequences.ints().map(Either::right).insert(100_000, Either.left("foo"))))
        ).isEqualTo("foo");
    }

    @Test
    void sequenceLeft() {
        assertThat(
            Either.merge(Sequences.left(Sequences.ints().map(Either::left).insert(100_000, Either.right("foo"))))
        ).isEqualTo("foo");
    }

    @Test
    void zipN() {
        assertThat(Sequences.zip(Sequence.empty())).isEqualTo(Sequence.of(Sequence.empty()));
        assertThat(Sequences.zip(Sequence.of(Sequence.empty()))).isEmpty();
        assertThat(Sequences.zip(Sequence.of(Sequences.range(0, 4)))).isEqualTo(Sequences.range(0, 4).map(Sequence::of));
    }
}
