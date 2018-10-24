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

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static io.github.gdejohn.procrastination.Functions.constant;
import static io.github.gdejohn.procrastination.Functions.gather;
import static io.github.gdejohn.procrastination.Functions.let;
import static io.github.gdejohn.procrastination.Pair.duplicate;
import static io.github.gdejohn.procrastination.Predicates.compose;
import static io.github.gdejohn.procrastination.Predicates.gather;
import static io.github.gdejohn.procrastination.Trampoline.call;
import static io.github.gdejohn.procrastination.Trampoline.terminate;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Utility methods for creating and working with sequences.
 *
 * <p>This class consists solely of static helper methods. It is not intended to be instantiated, so it has no public
 * constructors.
 */
public final class Sequences {
    private Sequences() {
        throw new AssertionError("this class is not intended to be instantiated");
    }

    /**
     * Lazily append to a sequence every element of another sequence.
     *
     * @see Sequences#interleave(Sequence, Sequence)
     * @see Sequences#concatenate(Sequence[])
     * @see Sequences#concatenate(Sequence)
     * @see Sequence#concatenate(Sequence)
     */
    public static <T> Sequence<T> concatenate(Sequence<? extends T> first, Sequence<? extends T> second) {
        return Sequence.<T>cast(first).concatenate(second);
    }

    /**
     * Iterated concatenation of an enumeration of sequences.
     *
     * @see Sequences#interleave(Sequence[])
     * @see Sequences#concatenate(Sequence)
     * @see Sequences#concatenate(Sequence, Sequence)
     * @see Sequence#concatenate(Sequence)
     */
    @SafeVarargs
    public static <T> Sequence<T> concatenate(Sequence<? extends T>... sequences) {
        return Sequences.concatenate(Sequence.from(sequences));
    }

    /**
     * Iterated concatenation of a sequence of sequences.
     *
     * @see Sequences#interleave(Sequence)
     * @see Sequences#concatenate(Sequence[])
     * @see Sequences#concatenate(Sequence, Sequence)
     * @see Sequence#concatenate(Sequence)
     */
    public static <T> Sequence<T> concatenate(Sequence<? extends Sequence<? extends T>> sequences) {
        return Sequence.lazy(
            () -> sequences.skipWhile(Sequence::isEmpty).match(
                (sequence, rest) -> Sequences.concatenate(sequence, Sequences.concatenate(rest)),
                Sequence.empty()
            )
        );
    }

    /**
     * Alternately draw elements from two sequences.
     *
     * @see Sequences#concatenate(Sequence, Sequence)
     * @see Sequence#interleave(Sequence)
     * @see Sequences#interleave(Sequence[])
     * @see Sequences#interleave(Sequence)
     */
    public static <T> Sequence<T> interleave(Sequence<? extends T> first, Sequence<? extends T> second) {
        return Sequence.<T>cast(first).interleave(second);
    }

    /**
     * Alternately draw elements from each sequence in an enumeration in round-robin fashion.
     *
     * @see Sequences#concatenate(Sequence[])
     * @see Sequence#interleave(Sequence)
     * @see Sequences#interleave(Sequence, Sequence)
     * @see Sequences#interleave(Sequence)
     */
    @SafeVarargs
    public static <T> Sequence<T> interleave(Sequence<? extends T>... sequences) {
        return Sequences.interleave(Sequence.from(sequences));
    }

    /**
     * Alternately draw elements from each sequence in a nested sequence in round-robin fashion.
     *
     * @see Sequences#concatenate(Sequence)
     * @see Sequence#interleave(Sequence)
     * @see Sequences#interleave(Sequence, Sequence)
     * @see Sequences#interleave(Sequence[])
     */
    public static <T> Sequence<T> interleave(Sequence<? extends Sequence<? extends T>> sequences) {
        return Sequences.concatenate(Sequences.zip(sequences));
    }

    /**
     * Pass each element of a sequence to every function in a sequence of functions.
     *
     * <p>This method differs from {@link Sequence#apply(Sequence) Sequence.apply()} in the order that it imposes on
     * the results.
     *
     * <p>This is the
     * <a href="http://hackage.haskell.org/packages/archive/base/latest/doc/html/Control-Applicative.html#v:-60--42--42--62-">{@code <**>}</a>
     * operator from Haskell, specialized to sequences.
     */
    public static <T, R> Sequence<R> apply(Sequence<? extends T> sequence, Sequence<? extends Function<? super T, ? extends R>> functions) {
        return sequence.flatMap(element -> functions.map(Functions.apply(element)));
    }

    /**
     * The Cartesian product of two sequences.
     *
     * <p>Pair each element of the first sequence with every element of the second sequence. The length of the
     * resulting sequence is the product of the lengths of the given sequences.
     *
     * @see Sequences#product(Sequence[])
     * @see Sequences#product(Sequence)
     * @see Sequence#apply(Sequence, BiPredicate, BiFunction)
     * @see Sequence#zip(Sequence)
     */
    public static <T, U> Sequence<Pair<T, U>> product(Sequence<? extends T> first, Sequence<? extends U> second) {
        return first.apply(second, Pair::of);
    }

    /**
     * The n-fold Cartesian product of an enumeration of sequences.
     *
     * @see Sequences#product(Sequence)
     * @see Sequences#product(Sequence)
     * @see Sequence#apply(Sequence, BiPredicate, BiFunction)
     */
    @SafeVarargs
    public static <T> Sequence<Sequence<T>> product(Sequence<? extends T>... sequences) {
        return Sequences.product(Sequence.from(sequences));
    }

    /**
     * The n-fold Cartesian product of a sequence of sequences.
     *
     * @see Sequences#product(Sequence)
     * @see Sequences#product(Sequence[])
     * @see Sequence#apply(Sequence, BiPredicate, BiFunction)
     */
    public static <T> Sequence<Sequence<T>> product(Sequence<? extends Sequence<? extends T>> sequences) {
        return Sequence.lazy(
            () -> sequences.match(
                (sequence, rest) -> sequence.flatMap(
                    element -> product(rest).map(Functions.apply(Sequence::cons, element))
                ),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    /**
     * Pair each element of one sequence with the corresponding element of another sequence, truncating whichever
     * sequence is longer.
     *
     * @see Sequence#zip(Sequence, BiPredicate, BiFunction)
     */
    public static <T, U> Sequence<Pair<T, U>> zip(Sequence<? extends T> first, Sequence<? extends U> second) {
        return first.zip(second, Pair::of);
    }

    /**
     * Fold into a sequence each of the corresponding elements of enumerated sequences.
     *
     * @see Sequences#zip(Sequence)
     * @see Sequences#zip(Sequence, Sequence)
     * @see Sequence#zip(Sequence)
     */
    @SafeVarargs
    public static <T> Sequence<Sequence<T>> zip(Sequence<? extends T>... sequences) {
        return Sequences.zip(Sequence.from(sequences));
    }

    /**
     * Fold into a sequence each of the corresponding elements of nested sequences.
     *
     * @see Sequences#zip(Sequence[])
     * @see Sequences#zip(Sequence, Sequence)
     * @see Sequence#zip(Sequence)
     */
    public static <T> Sequence<Sequence<T>> zip(Sequence<? extends Sequence<? extends T>> sequences) {
        return Sequence.lazy(
            () -> sequences.match(
                (first, rest) -> first.zip(
                    rest.foldRight(
                        Sequence.repeat(Sequence.<T>empty()),
                        (sequence, zipped) -> sequence.zip(zipped, Sequence::cons)
                    ),
                    Sequence::cons
                ),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    /** Split a sequence of pairs into a pair of sequences. */
    public static <T, U> Pair<Sequence<T>, Sequence<U>> unzip(Sequence<? extends Pair<? extends T, ? extends U>> pairs) {
        return pairs.unzip(Pair::first, Pair::second);
    }

    /** Filter a sequence of pairs and split into a pair of sequences. */
    public static <T, U> Pair<Sequence<T>, Sequence<U>> unzip(Sequence<? extends Pair<? extends T, ? extends U>> pairs, BiPredicate<? super T, ? super U> predicate) {
        return Sequences.unzip(pairs.filter(compose(gather(predicate), Pair::cast)));
    }

    /** Combine a variable number of futures into a future sequence. */
    @SafeVarargs
    public static <T> CompletableFuture<Sequence<T>> future(CompletableFuture<? extends T>... futures) {
        return future(Sequence.from(futures));
    }

    /** Combine a sequence of futures into a future sequence. */
    public static <T> CompletableFuture<Sequence<T>> future(Sequence<? extends CompletableFuture<? extends T>> futures) {
        return futures.foldRight(
            completedFuture(Sequence.empty()),
            (future, sequence) -> future.thenCombine(sequence, Sequence::cons)
        );
    }

    /** Extract the values from a sequence of maybes if every value exists. */
    public static <T> Maybe<Sequence<T>> maybe(Sequence<? extends Maybe<? extends T>> maybes) {
        return Maybe.lazy(
            () -> maybes.foldRight(
                Maybe.of(Sequence.empty()),
                maybe -> maybe.matchLazy(
                    value -> Either.right(
                        sequence -> sequence.match(
                            values -> Maybe.of(Sequence.cons(value, values)),
                            Maybe.empty()
                        )
                    ),
                    () -> Either.left(Maybe.empty())
                )
            )
        );
    }

    /**
     * Extract the values from a sequence of eithers if every value is on the right, otherwise return the first value
     * on the left.
     *
     * @see Sequences#left(Sequence)
     * @see Sequences#maybe(Sequence)
     * @see Sequences#partition(Sequence)
     */
    public static <A, B> Either<A, Sequence<B>> right(Sequence<? extends Either<? extends A, ? extends B>> eithers) {
        return Either.lazy(
            () -> eithers.foldRight(
                Either.right(Sequence.empty()),
                either -> either.matchLazy(
                    value -> Either.left(Either.left(value)),
                    value -> Either.right(
                        sequence -> sequence.match(
                            Either::left,
                            values -> Either.right(Sequence.cons(value, values))
                        )
                    )
                )
            )
        );
    }

    /**
     * Extract the values from a sequence of eithers if every value is on the left, otherwise return the first value on
     * the right.
     *
     * @see Sequences#right(Sequence)
     * @see Sequences#maybe(Sequence)
     * @see Sequences#partition(Sequence)
     */
    public static <A, B> Either<Sequence<A>, B> left(Sequence<? extends Either<? extends A, ? extends B>> eithers) {
        return Either.lazy(
            () -> eithers.foldRight(
                Either.left(Sequence.empty()),
                either -> either.matchLazy(
                    value -> Either.right(
                        sequence -> sequence.match(
                            values -> Either.left(Sequence.cons(value, values)),
                            Either::right
                        )
                    ),
                    value -> Either.left(Either.right(value))
                )
            )
        );
    }

    /**
     * Split a sequence of eithers into the sequence of values on the left and the sequence of values on the right.
     *
     * @see Sequences#right(Sequence)
     * @see Sequences#left(Sequence)
     */
    public static <A, B> Pair<Sequence<A>, Sequence<B>> partition(Sequence<? extends Either<? extends A, ? extends B>> eithers) {
        return Pair.duplicate(eithers::memoize).mapBoth(
            sequence -> sequence.flatMap(either -> either.matchLazy(Sequence::of, constant(Sequence.empty()))),
            sequence -> sequence.flatMap(either -> either.matchLazy(constant(Sequence.empty()), Sequence::of))
        );
    }

    /**
     * True if and only if no two elements at different indices are considered equal according to their natural order.
     *
     * <p>True if empty.
     *
     * @see Sequences#deduplicate(Sequence)
     * @see Sequence#pairwiseDistinct()
     * @see Sequence#pairwiseDistinct(Function)
     * @see Sequence#pairwiseDistinct(Comparator)
     */
    public static <T extends Comparable<? super T>> boolean pairwiseDistinct(Sequence<T> sequence) {
        return sequence.pairwiseDistinct(Comparator.naturalOrder());
    }

    /**
     * True if and only if each element is greater than or equal to every preceding element.
     *
     * <p>True if empty.
     *
     * @see Sequence#increasing(Comparator) Sequence.increasing(Comparator)
     * @see Sequences#strictlyIncreasing(Sequence) Sequence.strictlyIncreasing(Sequence)
     * @see Sequences#decreasing(Sequence) Sequence.decreasing(Sequence)
     * @see Sequences#strictlyDecreasing(Sequence) Sequence.strictlyDecreasing(Sequence)
     */
    public static <T extends Comparable<? super T>> boolean increasing(Sequence<T> sequence) {
        return sequence.increasing(Comparator.naturalOrder());
    }

    /**
     * True if and only if each element is strictly greater than every preceding element.
     *
     * <p>True if empty.
     *
     * @see Sequence#strictlyIncreasing(Comparator) Sequence.strictlyIncreasing(Comparator)
     * @see Sequences#increasing(Sequence) Sequence.increasing(Sequence)
     * @see Sequences#decreasing(Sequence) Sequence.decreasing(Sequence)
     * @see Sequences#strictlyDecreasing(Sequence) Sequence.strictlyDecreasing(Sequence)
     */
    public static <T extends Comparable<? super T>> boolean strictlyIncreasing(Sequence<T> sequence) {
        return sequence.strictlyIncreasing(Comparator.naturalOrder());
    }

    /**
     * True if and only if each element is less than or equal to every preceding element.
     *
     * <p>True if empty.
     *
     * @see Sequence#decreasing(Comparator) Sequence.decreasing(Comparator)
     * @see Sequences#increasing(Sequence) Sequence.increasing(Sequence)
     * @see Sequences#strictlyIncreasing(Sequence) Sequence.strictlyIncreasing(Sequence)
     * @see Sequences#strictlyDecreasing(Sequence) Sequence.strictlyDecreasing(Sequence)
     */
    public static <T extends Comparable<? super T>> boolean decreasing(Sequence<T> sequence) {
        return sequence.decreasing(Comparator.naturalOrder());
    }

    /**
     * True if and only if each element is strictly less than every preceding element.
     *
     * <p>True if empty.
     *
     * @see Sequence#strictlyDecreasing(Comparator) Sequence.strictlyDecreasing(Comparator)
     * @see Sequences#increasing(Sequence) Sequence.increasing(Sequence)
     * @see Sequences#strictlyIncreasing(Sequence) Sequence.strictlyIncreasing(Sequence)
     * @see Sequences#decreasing(Sequence) Sequence.decreasing(Sequence)
     */
    public static <T extends Comparable<? super T>> boolean strictlyDecreasing(Sequence<T> sequence) {
        return sequence.strictlyDecreasing(Comparator.naturalOrder());
    }

    /** The greatest element of a sequence according to the natural order of the elements, if the sequence is non-empty. */
    public static <T extends Comparable<? super T>> Maybe<T> maximum(Sequence<? extends T> sequence) {
        return Maybe.cast(sequence.maximum(Comparator.naturalOrder()));
    }

    /** The least element of a sequence according to the natural order of the elements, if the sequence is non-empty. */
    public static <T extends Comparable<? super T>> Maybe<T> minimum(Sequence<? extends T> sequence) {
        return Maybe.cast(sequence.minimum(Comparator.naturalOrder()));
    }

    /**
     * A sequence in which each element of this sequence that is considered distinct according to its natural order
     * appears exactly once.
     *
     * @see Sequences#pairwiseDistinct(Sequence)
     * @see Sequence#deduplicate()
     * @see Sequence#deduplicate(Function)
     * @see Sequence#deduplicate(Comparator)
     */
    public static <T extends Comparable<? super T>> Sequence<T> deduplicate(Sequence<? extends T> sequence) {
        return Sequence.cast(sequence.deduplicate(Comparator.naturalOrder()));
    }

    /**
     * Impose on a sequence the natural order of its elements.
     *
     * @see Sequence#sort(Comparator) Sequence.sort(Comparator)
     * @see Sequences#increasing(Sequence)
     * @see Sequences#strictlyIncreasing(Sequence)
     * @see Sequences#decreasing(Sequence)
     * @see Sequences#strictlyDecreasing(Sequence)
     */
    public static <T extends Comparable<? super T>> Sequence<T> sort(Sequence<? extends T> sequence) {
        return Sequence.cast(sequence.sort(Comparator.naturalOrder()));
    }

    /**
     * Split this sequence into runs of elements that are equivalent according to their natural order.
     *
     * @see Sequence#group()
     * @see Sequence#group(long)
     * @see Sequence#group(BiPredicate)
     * @see Sequence#group(Function)
     * @see Sequences#group(Sequence, Predicate)
     * @see Sequences#group(Sequence, Comparator)
     */
    public static <T extends Comparable<? super T>> Sequence<Sequence<T>> group(Sequence<? extends T> sequence) {
        return Sequences.group(sequence, Comparator.naturalOrder());
    }

    /**
     * Split this sequence into runs of elements that are equivalent according to a given order.
     *
     * @see Sequence#group()
     * @see Sequence#group(long)
     * @see Sequence#group(BiPredicate)
     * @see Sequence#group(Function)
     * @see Sequences#group(Sequence)
     * @see Sequences#group(Sequence, Predicate)
     */
    public static <T> Sequence<Sequence<T>> group(Sequence<? extends T> sequence, Comparator<? super T> comparator) {
        return Sequence.<T>cast(sequence).group(Predicates.equal(comparator));
    }

    /**
     * Split this sequence into runs of elements that are equivalent when projected through a predicate.
     *
     * @see Sequence#group()
     * @see Sequence#group(long)
     * @see Sequence#group(BiPredicate)
     * @see Sequence#group(Function)
     * @see Sequences#group(Sequence)
     * @see Sequences#group(Sequence, Comparator)
     */
    public static <T> Sequence<Sequence<T>> group(Sequence<? extends T> sequence, Predicate<? super T> predicate) {
        return Sequence.<T>cast(sequence).group((x, y) -> predicate.test(x) == predicate.test(y));
    }

    /**
     * Dictionary order for sequences of elements with a natural order.
     *
     * @see Sequences#lexicographically(Comparator) Sequence.lexicographically(Comparator)
     */
    public static <T extends Comparable<? super T>> Comparator<Sequence<T>> lexicographically() {
        return lexicographically(Comparator.naturalOrder());
    }

    /**
     * Dictionary order for sequences given an order on their elements.
     *
     * @see Sequences#lexicographically() Sequence.lexicographically()
     */
    public static <T> Comparator<Sequence<T>> lexicographically(Comparator<? super T> comparator) {
        return (left, right) -> Trampoline.evaluate(
            left,
            right,
            f -> x_xs -> y_ys -> x_xs.match(
                (x, xs) -> y_ys.match(
                    (y, ys) -> let(
                        comparator.compare(x, y),
                        comparison -> comparison == 0 ? call(f, xs, ys) : terminate(comparison)
                    ),
                    () -> terminate(1)
                ),
                () -> terminate(y_ys.isEmpty() ? 0 : -1)
            )
        );
    }

    /**
     * Adapt a function to work with sequences.
     *
     * <p>This method is equivalent to {@code f -> sequence -> sequence.map(f)}.
     *
     * @see Sequences#lift(BiFunction)
     * @see Sequence#map(Function)
     */
    public static <T, R> Function<Sequence<T>, Sequence<R>> lift(Function<? super T, ? extends R> function) {
        return sequence -> sequence.map(function);
    }

    /**
     * Adapt a binary function to work with sequences.
     *
     * <p>This method is equivalent to {@code f -> (xs, ys) -> ys.apply(xs.map(curry(f))}.
     *
     * @see Sequences#lift(Function)
     * @see Sequence#map(Function)
     * @see Sequence#apply(Sequence)
     * @see Sequence#apply(Sequence, BiFunction)
     * @see Functions#curry(BiFunction)
     */
    public static <T, U, R> BiFunction<Sequence<T>, Sequence<U>, Sequence<R>> lift(BiFunction<? super T, ? super U, ? extends R> function) {
        return (xs, ys) -> xs.apply(ys, function);
    }

    /**
     * Collect elements into a sequence, preserving any order imposed by the source.
     *
     * <p>All of the collector functions run in constant time, allowing optimal speedup for parallel reductions.
     */
    public static <T> Collector<T, ?, Sequence<T>> toSequence() {
        class SequenceBuilder {
            abstract class Node {
                abstract void append(T element);

                abstract void concatenate(SequenceBuilder elements);

                abstract Sequence<T> sequence();
            }

            private final Node EMPTY = new Node() {
                @Override
                void append(T element) {
                    SequenceBuilder.this.last = SequenceBuilder.this.first = new NonEmpty(element);
                }

                @Override
                void concatenate(SequenceBuilder elements) {
                    SequenceBuilder.this.first = elements.first;
                    SequenceBuilder.this.last = elements.last;
                }

                @Override
                Sequence<T> sequence() {
                    return Sequence.empty();
                }
            };

            class NonEmpty extends Node {
                private final T head;

                private Node tail = EMPTY;

                NonEmpty(T head) {
                    this.head = head;
                }

                @Override
                void append(T element) {
                    SequenceBuilder.this.last = this.tail = new NonEmpty(element);
                }

                @Override
                void concatenate(SequenceBuilder elements) {
                    this.tail = elements.first;
                    SequenceBuilder.this.last = elements.last;
                }

                @Override
                Sequence<T> sequence() {
                    return Sequence.cons(this.head, () -> this.tail.sequence());
                }
            }

            private Node first = this.EMPTY;

            private Node last = this.EMPTY;

            void append(T element) {
                requireNonNull(element, "sequences do not permit null elements");
                requireNonNull(this.last, "sequence builder cannot be reused");
                this.last.append(element);
            }

            SequenceBuilder concatenate(SequenceBuilder elements) {
                requireNonNull(this.last, "sequence builder cannot be reused");
                requireNonNull(elements.first, "sequence builder cannot be reused");
                requireNonNull(elements.last, "sequence builder cannot be reused");
                this.last.concatenate(elements);
                elements.first = null;
                elements.last = null;
                return this;
            }

            Sequence<T> build() {
                requireNonNull(this.first, "sequence builder cannot be reused");
                var sequence = this.first.sequence();
                this.first = null;
                this.last = null;
                return sequence;
            }
        }

        return new Collector<T, SequenceBuilder, Sequence<T>>() {
            @Override
            public Supplier<SequenceBuilder> supplier() {
                return SequenceBuilder::new;
            }

            @Override
            public BiConsumer<SequenceBuilder, T> accumulator() {
                return SequenceBuilder::append;
            }

            @Override
            public BinaryOperator<SequenceBuilder> combiner() {
                return SequenceBuilder::concatenate;
            }

            @Override
            public Function<SequenceBuilder, Sequence<T>> finisher() {
                return SequenceBuilder::build;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    /**
     * The nonnegative 32-bit signed integers.
     *
     * @see Sequences#longs()
     * @see Sequences#floats()
     * @see Sequences#doubles()
     * @see Sequences#rationals()
     */
    public static Sequence<Integer> ints() {
        return ints(0);
    }

    private static Sequence<Integer> ints(int origin) {
        return Sequence.cons(origin, () -> origin < Integer.MAX_VALUE ? ints(origin + 1) : Sequence.empty());
    }

    /**
     * The nonnegative 64-bit signed integers.
     *
     * @see Sequences#ints()
     * @see Sequences#floats()
     * @see Sequences#doubles()
     * @see Sequences#rationals()
     */
    public static Sequence<Long> longs() {
        return longs(0);
    }

    static Sequence<Long> longs(long origin) {
        return Sequence.cons(origin, () -> origin < Long.MAX_VALUE ? longs(origin + 1) : Sequence.empty());
    }

    /**
     * The positive finite 32-bit signed floating-point numbers.
     *
     * @see Sequences#ints()
     * @see Sequences#longs()
     * @see Sequences#doubles()
     * @see Sequences#rationals()
     */
    public static Sequence<Float> floats() {
        return floats(Float.MIN_VALUE);
    }

    private static Sequence<Float> floats(float f) {
        return Sequence.cons(f, () -> f < Float.MAX_VALUE ? floats(Math.nextUp(f)) : Sequence.empty());
    }

    /**
     * The positive finite 64-bit signed floating-point numbers.
     *
     * @see Sequences#ints()
     * @see Sequences#longs()
     * @see Sequences#floats()
     * @see Sequences#rationals()
     */
    public static Sequence<Double> doubles() {
        return doubles(Double.MIN_VALUE);
    }

    private static Sequence<Double> doubles(double d) {
        return Sequence.cons(d, () -> d < Double.MAX_VALUE ? doubles(Math.nextUp(d)) : Sequence.empty());
    }

    /**
     * The positive rational numbers.
     *
     * <p>This is the
     * <a href="https://en.wikipedia.org/wiki/Calkin%E2%80%93Wilf_tree#Breadth_first_traversal">Calkin-Wilf sequence</a>,
     * which produces each positive rational number exactly once, in finite time, as a reduced fraction (represented as
     * a pair of positive integers, where the first is the numerator and the second is the denominator).
     *
     * @see Sequences#ints()
     * @see Sequences#longs()
     * @see Sequences#rationals(BiFunction)
     * @see Sequences#rationals(BiPredicate, BiFunction)
     */
    public static Sequence<Pair<Long, Long>> rationals() {
        return Sequence.iterate(duplicate(1L), Functions.gather((p, q) -> Pair.of(q, (2 * (p / q) + 1) * q - p)));
    }

    /**
     * Map over the positive rational numbers.
     *
     * <p>Each rational number is passed to the given binary function (numerator first, denominator second) to produce
     * an element of this sequence.
     *
     * @see Sequences#rationals()
     * @see Sequences#rationals(BiPredicate, BiFunction)
     */
    public static <R> Sequence<R> rationals(BiFunction<? super Long, ? super Long, ? extends R> function) {
        return rationals().map(gather(function));
    }

    /**
     * Filter and map over the positive rational numbers.
     *
     * @see Sequences#rationals()
     * @see Sequences#rationals(BiFunction)
     */
    public static <R> Sequence<R> rationals(BiPredicate<? super Long, ? super Long> predicate, BiFunction<? super Long, ? super Long, ? extends R> function) {
        return rationals().filter(gather(predicate)).map(gather(function));
    }

    /**
     * Enumerate a closed, bounded interval of 32-bit signed integers, increasing if the first endpoint is less than
     * the second, otherwise decreasing.
     *
     * @see Sequences#range(long, long)
     */
    public static Sequence<Integer> range(int from, int to) {
        return Sequence.cons(
            from,
            () -> {
                if (from < to) {
                    return range(from + 1, to);
                } else if (from > to) {
                    return range(from - 1, to);
                } else {
                    return Sequence.empty();
                }
            }
        );
    }

    /**
     * Enumerate a closed, bounded interval of 64-bit signed integers, increasing if the first endpoint is less than
     * the second, otherwise decreasing.
     *
     * @see Sequences#range(int, int)
     */
    public static Sequence<Long> range(long from, long to) {
        return Sequence.cons(
            from,
            () -> {
                if (from < to) {
                    return range(from + 1, to);
                } else if (from > to) {
                    return range(from - 1, to);
                } else {
                    return Sequence.empty();
                }
            }
        );
    }

    /**
     * Inclusive contiguous slice of an enum, increasing if the first bound is declared before the second, otherwise
     * decreasing.
     */
    public static <T extends Enum<T>> Sequence<T> range(T from, T to) {
        T[] values = from.getDeclaringClass().getEnumConstants();
        return range(from.ordinal(), to.ordinal()).map(ordinal -> values[ordinal]);
    }
}
