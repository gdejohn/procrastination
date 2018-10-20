package io.github.gdejohn.procrastination;

import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.gdejohn.procrastination.Functions.compose;
import static io.github.gdejohn.procrastination.Functions.curry;
import static io.github.gdejohn.procrastination.Functions.fix;
import static io.github.gdejohn.procrastination.Functions.flip;
import static io.github.gdejohn.procrastination.Functions.gather;
import static io.github.gdejohn.procrastination.Functions.let;
import static io.github.gdejohn.procrastination.Functions.uncurry;
import static io.github.gdejohn.procrastination.Maybe.when;
import static io.github.gdejohn.procrastination.Pair.bySecond;
import static io.github.gdejohn.procrastination.Predicates.gather;
import static io.github.gdejohn.procrastination.Predicates.greaterThanOrEqualTo;
import static io.github.gdejohn.procrastination.Predicates.lessThan;
import static io.github.gdejohn.procrastination.Predicates.on;
import static io.github.gdejohn.procrastination.Trampoline.call;
import static io.github.gdejohn.procrastination.Trampoline.terminate;
import static io.github.gdejohn.procrastination.Undefined.undefined;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.lang.Math.multiplyExact;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;

/**
 * A lazily evaluated, persistent, ordered, homogeneous collection of zero or more non-null elements, possibly infinite,
 * allowing duplicates.
 *
 * <p>Sequences are recursively defined: they are either {@link Sequence#empty() empty}, or
 * {@link Sequence#cons(Object, Sequence) constructed} from a head element and a tail sequence. Conversely, the
 * instance method {@link Sequence#match(BiFunction, Supplier) match} pulls a sequence apart, simulating pattern
 * matching: if the sequence is non-empty, its head and tail are passed as arguments to a given binary function to
 * produce a result, otherwise the result is a given default value.
 *
 * <p>Sequences are lazy in the sense that they procrastinate: they put off work for as long as possible, only
 * computing each element on demand. They can also be {@link Sequence#memoize() memoized} such that each element is
 * computed at most once, the first time it is asked for, and then cached. Because sequences are lazy, it is perfectly
 * natural to work with infinite sequences.
 *
 * <p>Unlike {@link Stream streams}, sequences can be traversed any number of times. The trade-off is that sequences
 * derived from one-shot sources (e.g., iterators, streams) <em>must</em> be memoized.
 *
 * <p>This class offers no public or protected constructors, thereby prohibiting inheritance.
 *
 * @param <T> the type of the elements of this sequence
 *
 * @see Maybe
 */
public abstract class Sequence<T> implements Iterable<T> {
    private static abstract class Proxy<T> extends Sequence<T> {
        protected Proxy() {}

        protected abstract Sequence<T> principal();

        @Override
        public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
            return this.principal().matchLazy(uncons, otherwise);
        }

        @Override
        public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
            return this.principal().matchLazy(uncons, otherwise);
        }

        @Override
        public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
            return this.principal().match(uncons, otherwise);
        }

        @Override
        public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
            return this.principal().match(uncons, otherwise);
        }

        @Override
        public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, R otherwise) {
            return this.principal().matchNonEmpty(function, otherwise);
        }

        @Override
        public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
            return this.principal().matchNonEmpty(function, otherwise);
        }
    }

    private static final Sequence<?> EMPTY = new Sequence<>() {
        @Override
        public <R> R matchLazy(BiFunction<? super Supplier<Object>, ? super Sequence<Object>, ? extends R> uncons, R otherwise) {
            return otherwise;
        }

        @Override
        public <R> R matchLazy(BiFunction<? super Supplier<Object>, ? super Sequence<Object>, ? extends R> uncons, Supplier<? extends R> otherwise) {
            return otherwise.get();
        }

        @Override
        public Sequence<Object> memoize() {
            return this;
        }

        @Override
        public Sequence<Object> eager() {
            return this;
        }

        @Override
        public Iterator<Object> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public Spliterator<Object> spliterator() {
            return Spliterators.emptySpliterator();
        }

        @Override
        public Stream<Object> stream() {
            return Stream.empty();
        }
    };

    /**
     * The only constructor of this class, declared private so that the static factory methods declared inside of this
     * class can see it, but nothing else can.
     */
    private Sequence() {}

    /**
     * A sequence that delegates pattern matching to a lazily evaluated sequence.
     */
    public static <T> Sequence<T> lazy(Supplier<? extends Sequence<? extends T>> sequence) {
        return new Sequence.Proxy<>() {
            @Override
            protected Sequence<T> principal() {
                Sequence<T> principal = cast(sequence.get());
                while (principal instanceof Sequence.Proxy) {
                    principal = ((Sequence.Proxy<T>) principal).principal();
                }
                return principal.memoize();
            }
        };
    }

    /**
     * The empty sequence.
     *
     * <p>Analogous to the empty {@code Maybe}, or the unit value.
     *
     * @see Sequence#cons(Object, Sequence)
     * @see Sequence#of(Object)
     * @see Sequence#of(Object[])
     * @see Sequence#cons(Supplier, Sequence)
     * @see Sequence#of(Supplier)
     * @see Maybe#empty()
     * @see Unit#unit()
     */
    public static <T> Sequence<T> empty() {
        @SuppressWarnings("unchecked") // safe because the empty sequence never produces any elements
        var empty = (Sequence<T>) Sequence.EMPTY;
        return empty;
    }

    /**
     * Construct a non-empty sequence.
     *
     * @param head the first element of the sequence
     * @param tail the rest of the elements of the sequence
     *
     * @see Sequence#empty()
     * @see Sequence#of(Object)
     * @see Sequence#of(Object[])
     * @see Sequence#cons(Supplier, Sequence)
     * @see Sequence#of(Supplier)
     */
    public static <T> Sequence<T> cons(T head, Sequence<? extends T> tail) {
        requireNonNull(head);
        requireNonNull(tail);
        return new Sequence<>() {
            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply((Supplier<T>) () -> head, Sequence.cast(tail));
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply((Supplier<T>) () -> head, Sequence.cast(tail));
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply(head, Sequence.cast(tail));
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply(head, Sequence.cast(tail));
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, R otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> Maybe<R> matchNonEmpty(Function<? super Sequence<T>, ? extends R> function) {
                return Maybe.of(() -> function.apply(this));
            }

            @Override
            public Sequence<T> memoize() {
                return Sequence.cons(head, Sequence.memoize(tail));
            }
        };
    }

    /** Construct a non-empty sequence with a lazily evaluated tail. */
    public static <T> Sequence<T> cons(T head, Supplier<? extends Sequence<? extends T>> tail) {
        requireNonNull(head);
        requireNonNull(tail);
        return new Sequence<>() {
            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply((Supplier<T>) () -> head, Sequence.cast(tail.get()));
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply((Supplier<T>) () -> head, Sequence.cast(tail.get()));
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply(head, Sequence.cast(tail.get()));
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply(head, Sequence.cast(tail.get()));
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, R otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> Maybe<R> matchNonEmpty(Function<? super Sequence<T>, ? extends R> function) {
                return Maybe.of(() -> function.apply(this));
            }

            @Override
            public Sequence<T> memoize() {
                return Sequence.cons(head, Sequence.memoize(tail.get()));
            }
        };
    }

    /**
     * Construct a non-empty sequence with a lazily evaluated head.
     *
     * @param head the supplier of the first element of the sequence
     * @param tail the rest of the elements of the sequence
     *
     * @see Sequence#empty()
     * @see Sequence#cons(Object, Sequence)
     * @see Sequence#of(Object)
     * @see Sequence#of(Object[])
     * @see Sequence#of(Supplier)
     */
    public static <T> Sequence<T> cons(Supplier<? extends T> head, Sequence<? extends T> tail) {
        requireNonNull(head);
        requireNonNull(tail);
        return new Sequence<>() {
            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply(
                    Functions.map(Functions.memoize(head), Objects::requireNonNull),
                    Sequence.cast(tail)
                );
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply(
                    Functions.map(Functions.memoize(head), Objects::requireNonNull),
                    Sequence.cast(tail)
                );
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply(requireNonNull(head.get()), Sequence.cast(tail));
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply(requireNonNull(head.get()), Sequence.cast(tail));
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, R otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> Maybe<R> matchNonEmpty(Function<? super Sequence<T>, ? extends R> function) {
                return Maybe.of(() -> function.apply(this));
            }

            @Override
            public Sequence<T> memoize() {
                return Sequence.cons(Functions.memoize(head), Sequence.memoize(tail));
            }
        };
    }

    /** Construct a non-empty sequence with a lazily evaluated head and tail. */
    public static <T> Sequence<T> cons(Supplier<? extends T> head, Supplier<? extends Sequence<? extends T>> tail) {
        requireNonNull(head);
        requireNonNull(tail);
        return new Sequence<>() {
            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply(
                    Functions.map(Functions.memoize(head), Objects::requireNonNull),
                    Sequence.cast(tail.get())
                );
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply(
                    Functions.map(Functions.memoize(head), Objects::requireNonNull),
                    Sequence.cast(tail.get())
                );
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, R otherwise) {
                return uncons.apply(requireNonNull(head.get()), Sequence.cast(tail.get()));
            }

            @Override
            public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> uncons, Supplier<? extends R> otherwise) {
                return uncons.apply(requireNonNull(head.get()), Sequence.cast(tail.get()));
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, R otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(this);
            }

            @Override
            public <R> Maybe<R> matchNonEmpty(Function<? super Sequence<T>, ? extends R> function) {
                return Maybe.of(() -> function.apply(this));
            }

            @Override
            public Sequence<T> memoize() {
                return Sequence.cons(Functions.memoize(head), Sequence.memoize(tail.get()));
            }
        };
    }

    /**
     * A singleton sequence (i.e., a sequence with only one element).
     *
     * <p>{@code Sequence.of(head)} is equivalent to {@code Sequence.cons(head, Sequence.empty())}.
     *
     * @param head the only element of the sequence
     *
     * @see Sequence#of(Supplier)
     * @see Sequence#of(Object[])
     * @see Sequence#empty()
     * @see Sequence#cons(Object, Sequence)
     * @see Sequence#cons(Supplier, Sequence)
     * @see Sequence#only()
     * @see Sequence#isSingleton()
     */
    public static <T> Sequence<T> of(T head) {
        return Sequence.cons(head, Sequence.empty());
    }

    /**
     * A singleton sequence with a lazily evaluated head.
     *
     * <p>{@code Sequence.of(head)} is equivalent to {@code Sequence.cons(head, Sequence.empty())}.
     *
     * @param head the only, unevaluated element of the sequence
     *
     * @see Sequence#of(Object)
     * @see Sequence#of(Object[])
     * @see Sequence#empty()
     * @see Sequence#cons(Object, Sequence)
     * @see Sequence#cons(Supplier, Sequence)
     * @see Sequence#only()
     * @see Sequence#isSingleton()
     */
    public static <T> Sequence<T> of(Supplier<? extends T> head) {
        requireNonNull(head);
        return Sequence.cons(head, Sequence.empty());
    }

    /**
     * A sequence of zero or more elements.
     *
     * @param elements the elements of the sequence
     *
     * @see Sequence#from(Object[])
     * @see Sequence#empty()
     * @see Sequence#cons(Object, Sequence)
     * @see Sequence#cons(Supplier, Sequence)
     * @see Sequence#of(Object)
     * @see Sequence#of(Supplier)
     */
    @SafeVarargs
    public static <T> Sequence<T> of(T... elements) {
        return Sequence.from(elements);
    }

    /**
     * The empty sequence given a null argument, otherwise a singleton sequence of the argument.
     *
     * @param head the only element of the sequence if not null
     *
     * @see Sequence#from(Optional)
     */
    public static <T> Sequence<T> nullable(T head) {
        return head == null ? Sequence.empty() : Sequence.of(head);
    }

    /**
     * The empty sequence given a lazy argument that evaluates to null, otherwise a singleton sequence of the value.
     *
     * <p>Lazily matching against the returned {@code Sequence} does not preserve the laziness of the argument, because
     * it must be eagerly evaluated to decide whether the sequence is empty or not.
     */
    public static <T> Sequence<T> nullable(Supplier<? extends T> value) {
        return Sequence.lazy(() -> nullable(value.get()));
    }

    /**
     * A lazy view of an array as a sequence.
     */
    public static <T> Sequence<T> from(T[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of ints as a sequence.
     */
    public static Sequence<Integer> from(int[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of longs as a sequence.
     */
    public static Sequence<Long> from(long[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of shorts as a sequence.
     */
    public static Sequence<Short> from(short[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of bytes as a sequence.
     */
    public static Sequence<Byte> from(byte[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of chars as a sequence.
     */
    public static Sequence<Character> from(char[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of booleans as a sequence.
     */
    public static Sequence<Boolean> from(boolean[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of floats as a sequence.
     */
    public static Sequence<Float> from(float[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    /**
     * A lazy view of an array of doubles as a sequence.
     */
    public static Sequence<Double> from(double[] array) {
        return Sequence.from(index -> array[index], array.length, 0);
    }

    private static <T> Sequence<T> from(IntFunction<T> array, int length, int index) {
        if (index < length) {
            return Sequence.cons(() -> array.apply(index), () -> Sequence.from(array, length, index + 1));
        } else {
            return Sequence.empty();
        }
    }

    /**
     * A lazy view of an Iterable as a sequence.
     */
    public static <T> Sequence<T> from(Iterable<? extends T> iterable) {
        return Sequence.lazy(() -> Sequence.memoize(iterable.iterator()));
    }

    /**
     * A lazy view of a CharSequence as a sequence of code points, each represented as a string.
     *
     * @see CharSequence#codePoints()
     * @see Character#toChars(int)
     * @see String#String(char[])
     */
    public static Sequence<String> from(CharSequence characters) {
        return Sequence.lazy(() -> Sequence.memoize(characters.codePoints())).map(
            codePoint -> new String(Character.toChars(codePoint))
        );
    }

    /** A sequence of the values of an enum, in the order that they are declared. */
    public static <T extends Enum<T>> Sequence<T> from(Class<T> type) {
        return Sequence.from(requireNonNull(type.getEnumConstants()));
    }

    /**
     * A lazy view of a Map as a sequence of key-value pairs.
     */
    public static <K, V> Sequence<Pair<K, V>> from(Map<? extends K, ? extends V> map) {
        return Sequence.from(map.entrySet()).map(Pair::from);
    }

    /**
     * A lazy view of a Map as a sequence of key-value pairs, mapping over the pairs.
     */
    public static <K, V, R> Sequence<R> from(Map<? extends K, ? extends V> map, BiFunction<? super K, ? super V, ? extends R> function) {
        return Sequence.from(map.entrySet()).map(entry -> function.apply(entry.getKey(), entry.getValue()));
    }

    /**
     * A lazy view of a Map as a sequence of key-value pairs, filtering and mapping over the pairs.
     */
    public static <K, V, R> Sequence<R> from(Map<? extends K, ? extends V> map, BiPredicate<? super K, ? super V> predicate, BiFunction<? super K, ? super V, ? extends R> function) {
        return Sequence.from(
            map.entrySet()
        ).filter(
            entry -> predicate.test(entry.getKey(), entry.getValue())
        ).map(
            entry -> function.apply(entry.getKey(), entry.getValue())
        );
    }

    /**
     * A view of an {@code Optional} as either a singleton sequence containing the optional value if present, or an
     * empty sequence.
     *
     * @see Sequence#nullable(Object)
     * @see Maybe#from(Optional)
     * @see Maybe#sequence()
     */
    public static <T> Sequence<T> from(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<? extends T> optional) {
        return optional.map(Sequence::<T>of).orElse(Sequence.empty());
    }

    /**
     * A view of a {@code Callable} as either an empty sequence if {@link Callable#call()} throws an exception or
     * returns null, or a singleton sequence containing the result.
     *
     * @see Maybe#from(Callable)
     * @see Either#from(Callable)
     */
    public static <T> Sequence<T> from(Callable<? extends T> value) {
        return Sequence.lazy(
            () -> {
                try {
                    return Sequence.nullable(value.call());
                } catch (Exception exception) {
                    return Sequence.empty();
                }
            }
        );
    }

    /**
     * A view of a {@code CompletableFuture} as either an empty sequence if {@link CompletableFuture#get()} throws an
     * exception or returns null, or a singleton sequence containing the result.
     *
     * @see Maybe#from(CompletableFuture)
     * @see Either#from(CompletableFuture)
     */
    public static <T> Sequence<T> from(CompletableFuture<? extends T> value) {
        return Sequence.lazy(
            () -> {
                try {
                    return Sequence.nullable(value.get());
                } catch (Exception exception) {
                    return Sequence.empty();
                }
            }
        );
    }

    /** A lazy view of a Pair as a sequence. */
    public static <T> Sequence<T> from(Pair<? extends T, ? extends T> pair) {
        return Sequence.lazy(
            () -> pair.matchLazy((first, second) -> Sequence.cons(first, Sequence.of(second)))
        );
    }

    /**
     * A memoized sequence of the elements produced by an iterator.
     *
     * <p>The sequence must be {@link Sequence#memoize() memoized} because iterators can only be traversed once.
     */
    public static <T> Sequence<T> memoize(Iterator<? extends T> iterator) {
        return Sequence.lazy(
            () -> iterator.hasNext() ? Sequence.cons(iterator.next(), Sequence.memoize(iterator)) : Sequence.empty()
        ).memoize();
    }

    /**
     * A memoized sequence of the elements produced by a spliterator.
     *
     * <p>The sequence must be {@link Sequence#memoize() memoized} because spliterators can only be traversed once.
     */
    public static <T> Sequence<T> memoize(Spliterator<? extends T> spliterator) {
        return Sequence.memoize(Spliterators.iterator(spliterator));
    }

    /**
     * A memoized sequence of the elements produced by an enumeration.
     *
     * The sequence must be {@link Sequence#memoize() memoized} because enumerations can only be traversed once.
     */
    public static <T> Sequence<T> memoize(Enumeration<? extends T> enumeration) {
        return Sequence.memoize(enumeration.asIterator());
    }

    /**
     * A memoized sequence of the elements produced by a stream.
     *
     * <p>The sequence must be {@link Sequence#memoize() memoized} because streams can only be traversed once.
     *
     * <p>This method accepts any kind of {@link BaseStream} (e.g., {@link Stream}, {@link IntStream},
     * {@link LongStream}, {@link DoubleStream}, or custom implementations).
     */
    public static <T> Sequence<T> memoize(BaseStream<? extends T, ?> stream) {
        return Sequence.memoize(stream.iterator());
    }

    /**
     * Safe covariant cast.
     *
     * <p>Widen the element type of a sequence.
     *
     * @see Sequence#narrow(Class)
     */
    public static <T> Sequence<T> cast(Sequence<? extends T> sequence) {
        requireNonNull(sequence);
        @SuppressWarnings("unchecked") // safe because sequences are immutable
        var widened = (Sequence<T>) sequence;
        return widened;
    }

    /**
     * An infinite sequence of elements produced by a supplier.
     *
     * @see Sequence#generateOrdered(Supplier)
     * @see Sequence#repeat(Object)
     * @see Sequence#cycle()
     */
    public static <T> Sequence<T> generate(Supplier<? extends T> generator) {
        return Sequence.cons(generator, () -> Sequence.generate(generator));
    }

    /**
     * The elements produced by an infinite, ordered sequence of invocations of a supplier.
     *
     * <p>Within the scope of a traversal of the returned sequence, earlier elements are computed before later
     * elements.
     *
     * @see Sequence#generate(Supplier)
     */
    public static <T> Sequence<T> generateOrdered(Supplier<? extends T> generator) {
        return Sequence.lazy(() -> Sequence.cons(generator.get(), Sequence.generateOrdered(generator)));
    }

    /**
     * An infinite sequence where each element after the first is defined in terms of the preceding element.
     *
     * <p>For example, the natural numbers can be defined as {@code iterate(0, n -> n + 1)}.
     *
     * @param initial the first element of the sequence
     * @param next takes an element of the sequence and returns the next element
     *
     * @see Sequence#iterate(Object, BiFunction)
     * @see Sequence#iterate(Object, Predicate, Function)
     * @see Sequence#iterate(Object, Object, BiFunction)
     */
    public static <T> Sequence<T> iterate(T initial, Function<T, T> next) {
        return Sequence.cons(initial, () -> Sequence.iterate(next.apply(initial), next));
    }

    /**
     * An infinite sequence where each element after the first is defined in terms of its index and the preceding
     * element.
     *
     * <p>For example, the factorial sequence can be defined as {@code iterate(1L, (i, n) -> i * n)}.
     *
     * @param initial the first element of the sequence
     * @param next takes the next index and the previous element and returns the next element
     *
     * @see Sequence#iterate(Object, Function)
     * @see Sequence#iterate(Object, Predicate, Function)
     * @see Sequence#iterate(Object, Object, BiFunction)
     */
    public static <T> Sequence<T> iterate(T initial, BiFunction<? super Long, T, T> next) {
        return Sequences.longs(1).scanLeft(initial, flip(next));
    }

    /**
     * A sequence of zero or more elements where each element after the first is defined in terms of the preceding
     * element.
     *
     * <p>This method is analogous to for-loops. For example, these two statements are equivalent:
     *
     * <pre>{@code     for (int i = 0; i < 100; i++) System.out.println(i);
     *    iterate(0, i -> i < 100, i -> i + 1).forEach(System.out::println);}</pre>
     *
     * @param initial the first element of the sequence, if accepted by the predicate
     * @param condition given the next element, returns true if and only if the sequence should include it and continue
     * @param next takes an element and returns the next element
     *
     * @see Sequence#iterate(Object, Function)
     * @see Sequence#iterate(Object, BiFunction)
     * @see Sequence#iterate(Object, Object, BiFunction)
     */
    public static <T> Sequence<T> iterate(T initial, Predicate<? super T> condition, Function<T, T> next) {
        return Sequence.iterate(initial, next).takeWhile(condition);
    }

    /**
     * An infinite sequence where each element after the first two is defined in terms of the preceding two elements.
     *
     * <p>For example:
     *
     * <pre>{@code     Sequence<Integer> fibonacci = iterate(0, 1, Integer::sum);}</pre>
     *
     * @param first the first element of the sequence
     * @param second the second element of the sequence
     * @param next takes two consecutive elements of the sequence and returns the next element
     *
     * @see Sequence#iterate(Object, Function)
     * @see Sequence#iterate(Object, BiFunction)
     * @see Sequence#iterate(Object, Predicate, Function)
     */
    public static <T> Sequence<T> iterate(T first, T second, BiFunction<T, T, T> next) {
        return Sequence.cons(first, () -> Sequence.iterate(second, next.apply(first, second), next));
    }

    /**
     * An infinite sequence obtained by iteratively transforming a seed value into an element and a new seed.
     *
     * @param function takes a seed and returns the next element of the sequence and a new seed
     */
    public static <T, R> Sequence<R> unfold(T seed, Function<T, ? extends Pair<? extends R, T>> function) {
        return Sequence.lazy(
            () -> function.apply(seed).match(
                (element, next) -> Sequence.cons(element, Sequence.unfold(next, function))
            )
        );
    }

    /**
     * A sequence of zero or more elements obtained by iteratively transforming a seed value into an element and a new
     * seed.
     *
     * @param predicate takes a seed and returns false if and only if the sequence is finished
     * @param function takes a seed accepted by the predicate and returns the next element of the sequence and a new
     *                 seed
     */
    public static <T, R> Sequence<R> unfold(T seed, Predicate<? super T> predicate, Function<T, ? extends Pair<? extends R, T>> function) {
        return Sequence.lazy(
            () -> {
                if (predicate.test(seed)) {
                    return function.apply(seed).match(
                        (element, next) -> Sequence.cons(element, Sequence.unfold(next, predicate, function))
                    );
                } else {
                    return Sequence.empty();
                }
            }
        );
    }

    /**
     * An infinite sequence of the same element repeated endlessly.
     *
     * @param element the repeated element of the sequence
     */
    public static <T> Sequence<T> repeat(T element) {
        return Sequence.cons(element, () -> Sequence.repeat(element));
    }

    /**
     * True if and only if every element in a sequence of booleans is true.
     *
     * <p>True if empty.
     *
     * @see Sequence#all(Predicate)
     * @see Sequence#or(Sequence)
     * @see Sequence#product(Sequence)
     */
    public static boolean and(Sequence<Boolean> sequence) {
        return sequence.all(Boolean::booleanValue);
    }

    /**
     * True if and only if at least one element in a sequence of booleans is true.
     *
     * <p>False if empty.
     *
     * @see Sequence#any(Predicate)
     * @see Sequence#and(Sequence)
     * @see Sequence#sum(Sequence)
     */
    public static boolean or(Sequence<Boolean> sequence) {
        return sequence.any(Boolean::booleanValue);
    }

    /**
     * Iterated addition.
     *
     * @throws ArithmeticException if the result overflows a long
     *
     * @see Sequence#foldLeft(Object, BiFunction)
     * @see Sequence#product(Sequence)
     * @see Sequence#or(Sequence)
     */
    public static long sum(Sequence<Long> sequence) {
        return sequence.foldLeft(0L, Math::addExact);
    }

    /**
     * Iterated multiplication.
     *
     * @throws ArithmeticException if the result overflows a long
     *
     * @see Sequence#sum(Sequence)
     * @see Sequence#and(Sequence)
     */
    public static long product(Sequence<Long> sequence) {
        return Trampoline.evaluate(sequence, 1L,
            product -> seq -> n -> seq.match(
                (head, tail) -> head == 0 ? terminate(0L) : call(product, tail, multiplyExact(head, n)),
                () -> terminate(n)
            )
        );
    }

    /**
     * A sequence that only computes each element at most once, the first time it is asked for, delegating to this
     * sequence and caching the result.
     */
    public Sequence<T> memoize() {
        return Sequence.memoize(this);
    }

    private static <T> Sequence<T> memoize(Sequence<T> sequence) {
        class MemoizedSequence extends Sequence.Proxy<T> {
            private final Supplier<Sequence<T>> principal;

            MemoizedSequence(Supplier<Sequence<T>> principal) {
                this.principal = principal;
            }

            @Override
            protected Sequence<T> principal() {
                return this.principal.get();
            }

            @Override
            public Sequence<T> memoize() {
                return this;
            }
        }

        if (sequence instanceof MemoizedSequence) {
            return sequence;
        } else {
            Supplier<Sequence<T>> principal = Functions.memoize(
                () -> sequence.matchLazy(
                    (head, tail) -> Sequence.cons(
                        Functions.memoize(head),
                        tail.memoize()
                    ),
                    Sequence.empty()
                )
            );
            return new MemoizedSequence(principal);
        }
    }

    /** Force the evaluation of every element of this sequence. */
    public Sequence<T> eager() {
        return this.collect(Sequences.toSequence());
    }

    /**
     * Return a value defined in terms of the eagerly evaluated head and the tail of this sequence if it is non-empty,
     * otherwise return a lazy default value.
     *
     * <p>This method simulates pattern matching on this sequence, forcing evaluation of the head. If this sequence is
     * non-empty, apply the given binary function to the head and tail and return the result. Otherwise, return the
     * default value produced by the given supplier.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#head()
     * @see Sequence#tail()
     * @see Sequence#matchLazy(BiFunction,Supplier)
     * @see Sequence#match(BiFunction,Object)
     * @see Sequence#match(BiFunction)
     * @see Sequence#matchOrThrow(BiFunction)
     */
    public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
        return this.matchLazy(
            (head, tail) -> function.apply(head.get(), tail),
            otherwise
        );
    }

    /**
     * Return a value defined in terms of the eagerly evaluated head and the tail of this sequence if it is non-empty,
     * otherwise return an eager default value.
     *
     * <p>This method simulates pattern matching on this sequence, forcing evaluation of the head. If this sequence is
     * non-empty, apply the given binary function to the head and tail and return the result. Otherwise, return the
     * given default value.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#matchLazy(BiFunction,Object)
     * @see Sequence#match(BiFunction,Supplier)
     * @see Sequence#match(BiFunction)
     * @see Sequence#matchOrThrow(BiFunction)
     */
    public <R> R match(BiFunction<? super T, ? super Sequence<T>, ? extends R> function, R otherwise) {
        return this.matchLazy(
            (head, tail) -> function.apply(head.get(), tail),
            otherwise
        );
    }

    /**
     * Return a {@code Maybe} containing a value defined in terms of the eagerly evaluated head and the tail of this
     * sequence if it is non-empty.
     *
     * <p>If this sequence is non-empty, apply the given binary function to the head and tail and return a
     * {@link Maybe} containing the result. Otherwise, return an empty {@code Maybe}.
     *
     * @param <R> the type of the result if this sequence is non-empty
     *
     * @see Sequence#matchLazy(BiFunction)
     * @see Sequence#match(BiFunction,Supplier)
     * @see Sequence#match(BiFunction,Object)
     * @see Sequence#matchOrThrow(BiFunction)
     */
    public <R> Maybe<R> match(BiFunction<? super T, ? super Sequence<T>, ? extends R> function) {
        return Maybe.lazy(
            () -> this.matchLazy(
                (head, tail) -> Maybe.of(() -> function.apply(head.get(), tail)),
                Maybe.empty()
            )
        );
    }

    /**
     * Return a value defined in terms of the eagerly evaluated head and the tail of this sequence if it is non-empty,
     * otherwise throw an assertion error.
     *
     * @param <R> the type of the result
     *
     * @throws AssertionError if this sequence is empty
     *
     * @see Sequence#matchLazyOrThrow(BiFunction)
     * @see Sequence#matchOrThrow(BiFunction,Supplier)
     * @see Sequence#match(BiFunction)
     * @see Sequence#match(BiFunction,Supplier)
     * @see Sequence#match(BiFunction,Object)
     */
    public <R> R matchOrThrow(BiFunction<? super T, ? super Sequence<T>, ? extends R> function) {
        return this.match(
            function,
            () -> {
                throw new AssertionError("sequence is not non-empty");
            }
        );
    }

    /**
     * Return a value defined in terms of the eagerly evaluated head and the tail of this sequence if it is non-empty,
     * otherwise throw a supplied exception.
     *
     * @param <R> the type of the result
     * @param <X> the type of the exception
     *
     * @throws X if this sequence is empty
     *
     * @see Sequence#matchLazyOrThrow(BiFunction,Supplier)
     * @see Sequence#matchOrThrow(BiFunction)
     * @see Sequence#match(BiFunction)
     * @see Sequence#match(BiFunction,Supplier)
     * @see Sequence#match(BiFunction,Object)
     */
    public <R, X extends Throwable> R matchOrThrow(BiFunction<? super T, ? super Sequence<T>, ? extends R> function, Supplier<X> exception) throws X {
        try {
            return this.match(function, undefined());
        } catch (Undefined e) {
            throw exception.get();
        }
    }

    /**
     * Return a value defined in terms of the lazily evaluated head and the tail of this sequence if it is non-empty,
     * otherwise return a lazy default value.
     *
     * <p>This method simulates pattern matching on this sequence, deferring evaluation of the head. If this sequence
     * is non-empty, apply the given binary function to a supplier of the head and to the tail and return the result.
     * Otherwise, return the default value produced by the given supplier.
     *
     * <p>In contrast to {@link Sequence#match(BiFunction,Supplier) Sequence.match()}, this method is lazy with
     * respect to the head of this sequence. The caller of this method decides if and when to force evaluation of the
     * head. This is useful, for example, to preserve the laziness of an underlying sequence in terms of which another
     * value is lazily defined.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#match(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction,Object)
     * @see Sequence#matchLazy(BiFunction)
     * @see Sequence#matchLazyOrThrow(BiFunction)
     */
    public abstract <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise);

    /**
     * Return a value defined in terms of the lazily evaluated head and the tail of this sequence if it is non-empty,
     * otherwise return an eager default value.
     *
     * <p>This method simulates pattern matching on this sequence, deferring evaluation of the head. If this sequence
     * is non-empty, apply the given binary function to a supplier of the head and to the tail and return the result.
     * Otherwise, return the given default value.
     *
     * <p>In contrast to {@link Sequence#match(BiFunction,Object) Sequence.match()}, this method is lazy with
     * respect to the head of this sequence. The caller of this method decides if and when to force evaluation of the
     * head. This is useful, for example, to preserve the laziness of an underlying sequence in terms of which another
     * value is lazily defined.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#match(BiFunction,Object)
     * @see Sequence#matchLazy(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction)
     * @see Sequence#matchLazyOrThrow(BiFunction)
     */
    public abstract <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> function, R otherwise);

    /**
     * Return a {@code Maybe} containing a value defined in terms of the lazily evaluated head and the tail of this
     * sequence if it is non-empty.
     *
     * <p>If this sequence is non-empty, apply the given binary function to a supplier of the head and to the tail and
     * return a {@link Maybe} containing the result. Otherwise, return an empty {@code Maybe}.
     *
     * @param <R> the type of the result if this sequence is non-empty
     *
     * @see Sequence#match(BiFunction)
     * @see Sequence#matchLazy(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction,Object)
     * @see Sequence#matchLazyOrThrow(BiFunction)
     */
    public <R> Maybe<R> matchLazy(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> function) {
        return Maybe.lazy(
            () -> this.matchLazy(
                (head, tail) -> Maybe.of(() -> function.apply(head, tail)),
                Maybe.empty()
            )
        );
    }

    /**
     * Return a value defined in terms of the lazily evaluated head and the tail of this sequence if it is non-empty,
     * otherwise throw an assertion error.
     *
     * @param <R> the type of the result
     *
     * @throws AssertionError if this sequence is empty
     *
     * @see Sequence#matchOrThrow(BiFunction)
     * @see Sequence#matchLazyOrThrow(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction)
     * @see Sequence#matchLazy(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction,Object)
     */
    public <R> R matchLazyOrThrow(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> function) {
        return this.matchLazy(
            function,
            () -> {
                throw new AssertionError("sequence is not non-empty");
            }
        );
    }

    /**
     * Return a value defined in terms of the lazily evaluated head and the tail of this sequence if it is non-empty,
     * otherwise throw a supplied exception.
     *
     * @param <R> the type of the result
     * @param <X> the type of the exception
     *
     * @throws X if this sequence is empty
     *
     * @see Sequence#matchOrThrow(BiFunction, Supplier)
     * @see Sequence#matchLazyOrThrow(BiFunction)
     * @see Sequence#matchLazy(BiFunction)
     * @see Sequence#matchLazy(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction,Object)
     */
    public <R, X extends Throwable> R matchLazyOrThrow(BiFunction<? super Supplier<T>, ? super Sequence<T>, ? extends R> function, Supplier<X> exception) throws X {
        try {
            return this.matchLazy(function, undefined());
        } catch (Undefined e) {
            throw exception.get();
        }
    }

    /**
     * Return the result of applying a function to this sequence if it is non-empty, otherwise return a lazy default
     * value.
     *
     * <p>Use this method to distinguish between empty and non-empty sequences instead of
     * {@link Sequence#match(BiFunction,Supplier) Sequence.match()} or
     * {@link Sequence#matchLazy(BiFunction,Supplier) Sequence.matchLazy()} when extracting the head and tail isn't
     * required.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#match(BiFunction,Supplier)
     * @see Sequence#matchLazy(BiFunction,Supplier)
     * @see Sequence#matchNonEmpty(Function,Object)
     * @see Sequence#matchNonEmpty(Function)
     */
    public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, Supplier<? extends R> otherwise) {
        return this.matchLazy(
            (head, tail) -> function.apply(Sequence.cons(head, tail)),
            otherwise
        );
    }

    /**
     * Return the result of applying a function to this sequence if it is non-empty, otherwise return an eager default
     * value.
     *
     * <p>Use this method to distinguish between empty and non-empty sequences instead of
     * {@link Sequence#match(BiFunction,Object) Sequence.match()} or
     * {@link Sequence#matchLazy(BiFunction,Object) Sequence.matchLazy()} when extracting the head and tail isn't
     * required.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#match(BiFunction,Object)
     * @see Sequence#matchLazy(BiFunction,Object)
     * @see Sequence#matchNonEmpty(Function,Supplier)
     * @see Sequence#matchNonEmpty(Function)
     */
    public <R> R matchNonEmpty(Function<? super Sequence<T>, ? extends R> function, R otherwise) {
        return this.matchLazy(
            (head, tail) -> function.apply(Sequence.cons(head, tail)),
            otherwise
        );
    }

    /**
     * Return a {@code Maybe} containing the result of applying a function to this sequence if it is non-empty,
     * otherwise return an empty {@code Maybe}.
     *
     * <p>Use this method to distinguish between empty and non-empty sequences instead of
     * {@link Sequence#match(BiFunction) Sequence.match()} or
     * {@link Sequence#matchLazy(BiFunction) Sequence.matchLazy()} when extracting the head and tail isn't
     * required.
     *
     * @param <R> the type of the result
     *
     * @see Sequence#match(BiFunction)
     * @see Sequence#matchLazy(BiFunction)
     * @see Sequence#matchNonEmpty(Function,Supplier)
     * @see Sequence#matchNonEmpty(Function,Object)
     */
    public <R> Maybe<R> matchNonEmpty(Function<? super Sequence<T>, ? extends R> function) {
        return Maybe.lazy(
            () -> this.matchNonEmpty(sequence -> Maybe.of(() -> function.apply(sequence)), Maybe.empty())
        );
    }

    /**
     * The head and tail of this sequence, if this sequence is non-empty.
     *
     * @see Sequence#head()
     * @see Sequence#tail()
     * @see Sequence#match(BiFunction)
     * @see Sequence#matchLazy(BiFunction)
     */
    public Maybe<Pair<T, Sequence<T>>> uncons() {
        return this.matchLazy(Pair::of);
    }

    /** Perform an action on each element of this sequence. */
    @Override
    public void forEach(Consumer<? super T> action) {
        this.spliterator().forEachRemaining(action);
    }

    /** If non-empty, perform an action on each element of this sequence, otherwise perform a default action. */
    public void forEachOrElse(Consumer<? super T> action, Runnable otherwise) {
        this.match(
            (head, tail) -> {
                action.accept(head);
                tail.forEach(action);
                return unit();
            },
            () -> {
                otherwise.run();
                return unit();
            }
        );
    }

    /**
     * A lazy view of this sequence as a stream.
     *
     * @see Sequence#list()
     * @see Sequence#array(IntFunction)
     * @see Sequence#spliterator()
     * @see Sequence#iterator()
     */
    public Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * A lazy, copy-on-read mutable view of this sequence as a sequential-access list that prohibits null elements.
     *
     * <p>The returned list gets filled in on the fly, delegating to this sequence to compute each element just in time
     * when first requested. Structural modifications of the list (adding, removing, or replacing elements) do not
     * affect this sequence (nor could they, because sequences are structurally immutable). Manual synchronization is
     * required to safely share the list among multiple threads.
     *
     * @see Sequence#array(IntFunction)
     * @see Sequence#stream()
     * @see Sequence#iterator()
     * @see Sequence#spliterator()
     * @see Collections#synchronizedList(List)
     */
    public List<T> list() {
        return Sequence.list(this);
    }

    private static <T> List<T> list(Sequence<T> sequence) {
        class SequenceList extends AbstractSequentialList<T> {
            private final List<T> list = new LinkedList<>();

            private Spliterator<T> spliterator;

            SequenceList(Sequence<T> sequence) {
                this.spliterator = sequence.spliterator();
            }

            @Override
            public boolean isEmpty() {
                return this.list.isEmpty() && !this.spliterator.tryAdvance(this.list::add);
            }

            @Override
            public int size() {
                this.spliterator.forEachRemaining(this.list::add);
                return this.list.size();
            }

            @Override
            public boolean add(T element) {
                requireNonNull(element);
                this.spliterator.forEachRemaining(this.list::add);
                return this.list.add(element);
            }

            @Override
            public void clear() {
                this.spliterator = Spliterators.emptySpliterator();
                this.list.clear();
            }

            @Override
            public int lastIndexOf(Object element) {
                if (element == null) {
                    return -1;
                } else {
                    this.spliterator.forEachRemaining(this.list::add);
                    return this.list.lastIndexOf(element);
                }
            }

            @Override
            public List<T> subList(int from, int to) {
                if (from < 0) {
                    throw new IndexOutOfBoundsException();
                } else if (from > to) {
                    throw new IllegalArgumentException();
                } else {
                    this.listIterator(to);
                    return this.list.subList(from, to);
                }
            }

            @Override
            public Iterator<T> iterator() {
                return this.listIterator();
            }

            @Override
            public ListIterator<T> listIterator() {
                return this.listIterator(0);
            }

            @Override
            public ListIterator<T> listIterator(int index) {
                if (index < 0) {
                    throw new IndexOutOfBoundsException(index);
                } else {
                    return new ListIterator<>() {
                        private final ListIterator<T> iterator;

                        {
                            int size = SequenceList.this.list.size();
                            if (index <= size) {
                                this.iterator = SequenceList.this.list.listIterator(index);
                            } else {
                                this.iterator = SequenceList.this.list.listIterator(size);
                                advance(index - size);
                            }
                        }

                        private void advance(int steps) {
                            while (steps-- > 0) {
                                if (this.hasNext()) {
                                    this.next();
                                } else {
                                    throw new IndexOutOfBoundsException(steps);
                                }
                            }
                        }

                        @Override
                        public boolean hasNext() {
                            if (this.iterator.hasNext()) {
                                return true;
                            } else if (SequenceList.this.spliterator.tryAdvance(this.iterator::add)) {
                                this.iterator.previous();
                                return true;
                            } else {
                                return false;
                            }
                        }

                        @Override
                        public T next() {
                            if (this.hasNext()) {
                                return this.iterator.next();
                            } else {
                                throw new NoSuchElementException();
                            }
                        }

                        @Override
                        public boolean hasPrevious() {
                            return this.iterator.hasPrevious();
                        }

                        @Override
                        public T previous() {
                            return this.iterator.previous();
                        }

                        @Override
                        public int nextIndex() {
                            return this.iterator.nextIndex();
                        }

                        @Override
                        public int previousIndex() {
                            return this.iterator.previousIndex();
                        }

                        @Override
                        public void remove() {
                            this.iterator.remove();
                        }

                        @Override
                        public void set(T element) {
                            requireNonNull(element);
                            this.iterator.set(element);
                        }

                        @Override
                        public void add(T element) {
                            requireNonNull(element);
                            this.iterator.add(element);
                        }
                    };
                }
            }

            @Override
            public Spliterator<T> spliterator() {
                return Spliterators.spliteratorUnknownSize(this.iterator(), NONNULL | ORDERED);
            }

            @Override
            public Stream<T> stream() {
                return StreamSupport.stream(this.spliterator(), false);
            }

            @Override
            public Stream<T> parallelStream() {
                return this.stream();
            }
        }

        return new SequenceList(sequence);
    }

    /**
     * An array of the elements of this sequence.
     *
     * @param factory returns an array with component type {@code A} and the given length
     *
     * @param <A> the component type of the array
     *
     * @throws ArrayStoreException if the runtime component type of the array returned by the factory is not a
     *                             supertype of the runtime type of every element in this sequence
     *
     * @see Sequence#list()
     * @see Sequence#stream()
     * @see Sequence#iterator()
     * @see Sequence#spliterator()
     */
    public <A> A[] array(IntFunction<A[]> factory) {
        //noinspection SuspiciousToArrayCall
        return this.stream().toArray(factory);
    }

    /**
     * A late-binding spliterator over this sequence.
     *
     * @see Sequence#iterator()
     * @see Sequence#stream()
     * @see Sequence#list()
     * @see Sequence#array(IntFunction)
     */
    @Override
    public Spliterator<T> spliterator() {
        return Sequence.spliterator(this);
    }

    private static <T> Spliterator<T> spliterator(Sequence<T> sequence) {
        class SequenceSpliterator implements Spliterator<T> {
            private Sequence<T> sequence;

            SequenceSpliterator(Sequence<T> sequence) {
                this.sequence = sequence;
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                return this.sequence.match(
                    (head, tail) -> {
                        action.accept(head);
                        this.sequence = tail;
                        return true;
                    },
                    () -> {
                        this.sequence = Sequence.empty();
                        return false;
                    }
                );
            }

            @Override
            public Spliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return NONNULL;
            }
        }

        return new SequenceSpliterator(sequence);
    }

    /**
     * An unmodifiable iterator over this sequence.
     *
     * <p>The returned iterator throws {@link UnsupportedOperationException} on invocation of {@link Iterator#remove()
     * remove()}.
     *
     * @see Sequence#spliterator()
     * @see Sequence#list()
     * @see Sequence#array(IntFunction)
     * @see Sequence#stream()
     */
    @Override
    public Iterator<T> iterator() {
        return Sequence.iterator(this);
    }

    private static <T> Iterator<T> iterator(Sequence<T> sequence) {
        class SequenceIterator implements Iterator<T> {
            private Sequence<T> sequence;

            SequenceIterator(Sequence<T> sequence) {
                this.sequence = sequence;
            }

            @Override
            public boolean hasNext() {
                return this.sequence.matchLazy(
                    (head, tail) -> {
                        this.sequence = Sequence.cons(head, tail);
                        return true;
                    },
                    () -> {
                        this.sequence = Sequence.empty();
                        return false;
                    }
                );
            }

            @Override
            public T next() {
                return this.sequence.matchOrThrow(
                    (head, tail) -> {
                        this.sequence = tail;
                        return head;
                    },
                    NoSuchElementException::new
                );
            }

            @Override
            public void forEachRemaining(Consumer<? super T> action) {
                this.sequence.forEach(action);
                this.sequence = Sequence.empty();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }

        return new SequenceIterator(sequence);
    }

    /**
     * Return true if and only if the argument is a sequence and contains the same elements as this sequence in the
     * same order.
     *
     * If this sequence is infinite and the argument is also an infinite sequence, they must not be equal, or this
     * method will never return.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof Sequence) {
            //noinspection RedundantCast
            return Trampoline.evaluate(this, (Sequence<?>) object,
                equal -> sequence -> other -> sequence.matchLazy(
                    (x, xs) -> other.match(
                        (y, ys) -> x.get().equals(y) ? call(equal, xs, ys) : terminate(false),
                        () -> terminate(false)
                    ),
                    () -> terminate(other.isEmpty())
                )
            );
        } else {
            return false;
        }
    }

    /**
     * Return the hash code of this sequence, following the contract of {@code List.hashCode()}.
     *
     * If this sequence is infinite, this method will never return.
     *
     * @see List#hashCode()
     */
    @Override
    public int hashCode() {
        return this.foldLeft(1, (hash, element) -> 31 * hash + element.hashCode());
    }

    /**
     * Return the string representation of this sequence, truncating if there are more than thirty elements.
     *
     * @see Sequence#toString(String)
     * @see Sequence#toString(String, String, String)
     */
    @Override
    public String toString() {
        var sequence = this.memoize();
        if (sequence.longerThan(30)) {
            return sequence.take(30).toString(", ", "(", ", ...)");
        } else {
            return sequence.toString(", ", "(", ")");
        }
    }

    /**
     * Concatenate the string representations of the elements of this sequence, separating the elements with a
     * delimiter.
     *
     * If this sequence is infinite, this method will never return.
     *
     * @see Sequence#toString(String, String, String)
     * @see Collectors#joining(CharSequence)
     */
    public String toString(String delimiter) {
        return this.collect(mapping(Object::toString, joining(delimiter)));
    }

    /**
     * Concatenate the string representations of the elements of this sequence, with a given delimiter, prefix, and
     * suffix.
     *
     * If this sequence is infinite, this method will never return.
     *
     * @see Sequence#toString(String)
     * @see Collectors#joining(CharSequence, CharSequence, CharSequence)
     */
    public String toString(String delimiter, String prefix, String suffix) {
        return this.collect(mapping(Object::toString, joining(delimiter, prefix, suffix)));
    }

    /**
     * The nonnegative number of elements in this sequence.
     *
     * <p>If this sequence is memoized and strongly reachable, this method might throw an {@link OutOfMemoryError}.
     *
     * @see Sequence#length(long)
     *
     * @throws ArithmeticException if the length overflows a long
     */
    public long length() {
        return Trampoline.evaluate(this, 0L,
            length -> sequence -> n -> sequence.matchLazy(
                (head, tail) -> call(length, tail, Math.incrementExact(n)),
                () -> terminate(n)
            )
        );
    }

    /** The nonnegative number of elements in this sequence, if less than or equal to a given bound. */
    public Maybe<Long> length(long bound) {
        if (bound == Long.MAX_VALUE) {
            return Maybe.from(this::length);
        } else if (bound >= 0) {
            return Maybe.lazy(
                let(
                    this.take(bound + 1),
                    sequence -> () -> let(sequence.length(), length -> when(length <= bound, length))
                )
            );
        } else {
            return Maybe.empty();
        }
    }

    /**
     * True if and only if this sequence is longer than a given length.
     *
     * This is more efficient than comparing against {@link Sequence#length Sequence.length()} because it does not
     * necessarily traverse the entire sequence, which also means that it terminates on infinite sequences.
     *
     * @see Sequence#shorterThan(long)
     */
    public boolean longerThan(long length) {
        return this.length(length).isEmpty();
    }

    /**
     * True if and only if this sequence has more elements than the given sequence.
     *
     * This is more efficient than comparing {@link Sequence#length lengths} because it does not necessarily traverse
     * the entirety of both sequences, which also means that it terminates if at least one of the sequences is finite.
     *
     * @see Sequence#shorterThan(Sequence)
     */
    public boolean longerThan(Sequence<?> sequence) {
        return Trampoline.evaluate(this, sequence,
            longer -> seq -> other -> seq.matchLazy(
                (x, xs) -> other.matchLazy(
                    (y, ys) -> call(longer, xs, ys),
                    () -> terminate(true)
                ),
                () -> terminate(false)
            )
        );
    }

    /**
     * True if and only if this sequence is shorter than a given length.
     *
     * This is more efficient than comparing against {@link Sequence#length Sequence.length()} because it does not
     * necessarily traverse the entire sequence, which also means that it terminates on infinite sequences.
     *
     * @see Sequence#longerThan(long)
     */
    public boolean shorterThan(long length) {
        return !this.longerThan(length - 1);
    }

    /**
     * True if and only if this sequence has fewer elements than the given sequence.
     *
     * This is more efficient than comparing {@link Sequence#length lengths} because it does not necessarily traverse
     * the entirety of both sequences, which also means that it terminates if at least one of the sequences is finite.
     *
     * @see Sequence#longerThan(Sequence)
     */
    public boolean shorterThan(Sequence<?> sequence) {
        return sequence.longerThan(this);
    }

    /**
     * True if and only if there are no elements in this sequence.
     */
    public boolean isEmpty() {
        return this.matchLazy(
            (head, tail) -> false,
            true
        );
    }

    /**
     * True if and only if there is exactly one element in this sequence.
     *
     * @see Sequence#only
     * @see Sequence#of(Object)
     * @see Sequence#of(Supplier)
     */
    public boolean isSingleton() {
        return this.matchLazy(
            (head, tail) -> tail.isEmpty(),
            false
        );
    }

    /**
     * True if and only if at least one of the elements in this sequence satisfies a predicate.
     *
     * <p>False if this sequence is empty.
     *
     * @see Sequence#or(Sequence)
     */
    public boolean any(Predicate<? super T> predicate) {
        return Trampoline.evaluate(this,
            any -> sequence -> sequence.match(
                (head, tail) -> predicate.test(head) ? terminate(true) : call(any, tail),
                () -> terminate(false)
            )
        );
    }

    /**
     * True if and only if every element in this sequence satisfies a predicate.
     *
     * <p>True if this sequence is empty.
     *
     * @see Sequence#and(Sequence)
     */
    public boolean all(Predicate<? super T> predicate) {
        return !this.any(predicate.negate());
    }

    /**
     * True if and only if each element is greater than or equal to every preceding element according to a comparator.
     *
     * <p>True if this sequence is empty.
     *
     * @see Sequences#increasing(Sequence)
     * @see Sequence#strictlyIncreasing(Comparator)
     * @see Sequence#decreasing(Comparator)
     * @see Sequence#strictlyDecreasing(Comparator)
     */
    public boolean increasing(Comparator<? super T> comparator) {
        return and(this.zip(Predicates.increasing(comparator)::test));
    }

    /**
     * True if and only if each element is strictly greater than every preceding element according to a comparator.
     *
     * <p>True if this sequence is empty.
     *
     * @see Sequences#strictlyIncreasing(Sequence)
     * @see Sequence#increasing(Comparator)
     * @see Sequence#decreasing(Comparator)
     * @see Sequence#strictlyDecreasing(Comparator)
     */
    public boolean strictlyIncreasing(Comparator<? super T> comparator) {
        return and(this.zip(Predicates.strictlyIncreasing(comparator)::test));
    }

    /**
     * True if and only if each element is less than or equal to every preceding element according to a comparator.
     *
     * <p>True if this sequence is empty.
     *
     * @see Sequences#decreasing(Sequence)
     * @see Sequence#increasing(Comparator)
     * @see Sequence#strictlyIncreasing(Comparator)
     * @see Sequence#strictlyDecreasing(Comparator)
     */
    public boolean decreasing(Comparator<? super T> comparator) {
        return this.increasing(comparator.reversed());
    }

    /**
     * True if and only if each element is strictly less than every preceding element according to a comparator.
     *
     * <p>True if this sequence is empty.
     *
     * @see Sequences#strictlyDecreasing(Sequence)
     * @see Sequence#increasing(Comparator)
     * @see Sequence#strictlyIncreasing(Comparator)
     * @see Sequence#decreasing(Comparator)
     */
    public boolean strictlyDecreasing(Comparator<? super T> comparator) {
        return this.strictlyIncreasing(comparator.reversed());
    }

    /**
     * True if and only if none of the elements in this sequence are repeated.
     *
     * @see Sequence#pairwiseDistinct(Function)
     * @see Sequence#pairwiseDistinct(Comparator)
     * @see Sequences#pairwiseDistinct(Sequence)
     * @see Sequence#deduplicate()
     */
    public boolean pairwiseDistinct() {
        return this.all(new HashSet<>()::add);
    }

    /**
     * True if and only if no two elements at different indices of this sequence are equal when projected through a
     * function.
     *
     * @see Sequence#pairwiseDistinct()
     * @see Sequence#pairwiseDistinct(Comparator)
     * @see Sequences#pairwiseDistinct(Sequence)
     * @see Sequence#deduplicate(Function)
     */
    public boolean pairwiseDistinct(Function<? super T, ?> function) {
        return this.all(Predicates.compose(new HashSet<>()::add, function));
    }

    /**
     * True if and only if no two elements at different indices of this sequence are considered equal by a comparator.
     *
     * @see Sequence#pairwiseDistinct()
     * @see Sequence#pairwiseDistinct(Function)
     * @see Sequences#pairwiseDistinct(Sequence)
     * @see Sequence#deduplicate(Comparator)
     */
    public boolean pairwiseDistinct(Comparator<? super T> comparator) {
        return this.all(new TreeSet<>(comparator)::add);
    }

    /**
     * True if and only if the argument is an element of this sequence.
     *
     * @see Sequence#containsAny(Sequence)
     * @see Sequence#containsAll(Sequence)
     */
    public boolean contains(T element) {
        return this.any(Predicate.isEqual(element));
    }

    /**
     * True if and only if at least one of the given elements is an element of this sequence.
     *
     * @see Sequence#contains(Object)
     * @see Sequence#containsAll(Sequence)
     */
    public boolean containsAny(Sequence<? extends T> sequence) {
        return this.matchNonEmpty(compose(sequence::any, Sequence::in), false);
    }

    /**
     * True if and only if all of the given elements are elements of this sequence.
     *
     * @see Sequence#contains(Object)
     * @see Sequence#containsAny(Sequence)
     */
    public boolean containsAll(Sequence<? extends T> sequence) {
        return sequence.all(in(this));
    }

    private static <T> Predicate<T> in(Sequence<T> sequence) {
        class Membership implements Predicate<T> {
            private final Set<T> seen = new HashSet<>();

            private Sequence<T> rest;

            Membership(Sequence<T> sequence) {
                this.rest = sequence;
            }

            @Override
            public boolean test(T element) {
                if (this.seen.contains(element)) {
                    return true;
                } else {
                    return Trampoline.evaluate(
                        this.rest,
                        contains -> seq -> seq.match(
                            (head, tail) -> {
                                this.seen.add(head);
                                if (head.equals(element)) {
                                    this.rest = tail;
                                    return terminate(true);
                                } else {
                                    return call(contains, tail);
                                }
                            },
                            () -> {
                                this.rest = Sequence.empty();
                                return terminate(false);
                            }
                        )
                    );
                }
            }
        }

        return new Membership(sequence);
    }

    /**
     * True if and only if all of the given elements appear consecutively and in the same order at the beginning of
     * this sequence.
     */
    public boolean hasPrefix(Sequence<? extends T> prefix) {
        return Trampoline.evaluate(this, prefix,
            hasPrefix -> sequence -> other -> other.matchLazy(
                (y, ys) -> sequence.match(
                    (x, xs) -> x.equals(y.get()) ? call(hasPrefix, xs, ys) : terminate(false),
                    () -> terminate(false)
                ),
                () -> terminate(true)
            )
        );
    }

    /**
     * True if and only if all of the given elements appear consecutively and in the same order at the end of this
     * sequence.
     */
    public boolean hasSuffix(Sequence<? extends T> suffix) {
        return this.reverse().hasPrefix(suffix.reverse());
    }

    /**
     * True if and only if all of the given elements appear consecutively and in the same order somewhere in this
     * sequence.
     */
    public boolean hasInfix(Sequence<? extends T> infix) {
        var sequence = infix.memoize();
        return this.suffixes().any(suffix -> suffix.hasPrefix(sequence));
    }

    /** True if and only if all of the given elements appear somewhere in this sequence in their same order. */
    public boolean hasSubsequence(Sequence<? extends T> subsequence) {
        return Trampoline.evaluate(this, subsequence,
            hasSubsequence -> sequence -> other -> other.matchLazy(
                (y, ys) -> sequence.match(
                    (x, xs) -> x.equals(y.get()) ? call(hasSubsequence, xs, ys) : call(
                        hasSubsequence,
                        xs,
                        other
                    ),
                    () -> terminate(false)
                ),
                () -> terminate(true)
            )
        );
    }

    /**
     * The first element of this sequence, if this sequence is non-empty.
     *
     * @see Sequence#last()
     * @see Sequence#tail()
     * @see Sequence#initial()
     * @see Sequence#uncons()
     */
    public Maybe<T> head() {
        return this.match((head, tail) -> head);
    }

    /**
     * The last element of this sequence, if this sequence is non-empty.
     *
     * @see Sequence#head()
     * @see Sequence#tail()
     * @see Sequence#initial()
     */
    public Maybe<T> last() {
        return Maybe.lazy(
            () -> Trampoline.evaluate(this,
                last -> sequence -> sequence.matchLazy(
                    (head, tail) -> tail.matchNonEmpty(
                        rest -> call(last, rest),
                        () -> terminate(Maybe.of(head))
                    ),
                    () -> terminate(Maybe.empty())
                )
            )
        );
    }

    /**
     * The head of this sequence, if it exists and the tail is empty.
     *
     * @see Sequence#isSingleton
     * @see Sequence#of(Object)
     * @see Sequence#of(Supplier)
     */
    public Maybe<T> only() {
        return Maybe.join(this.matchLazy((head, tail) -> when(tail.isEmpty(), head)));
    }

    /** The element of this sequence at a given index, if the index is in bounds. */
    public Maybe<T> element(long index) {
        return index < 0 ? Maybe.empty() : Maybe.lazy(
            () -> Trampoline.evaluate(this, index,
                element -> sequence -> n -> sequence.matchLazy(
                    (head, tail) -> n == 0 ? terminate(Maybe.of(head)) : call(element, tail, n - 1),
                    () -> terminate(Maybe.empty())
                )
            )
        );
    }

    /** The greatest element of this sequence according to a comparator, if this sequence is non-empty. */
    public Maybe<T> maximum(Comparator<? super T> comparator) {
        return Maybe.lazy(() -> this.foldLeft(Functions.maximum(comparator)));
    }

    /** The least element of this sequence according to a comparator, if this sequence is non-empty. */
    public Maybe<T> minimum(Comparator<? super T> comparator) {
        return this.maximum(comparator.reversed());
    }

    /** The first element of this sequence that satisfies a predicate, if such an element exists. */
    public Maybe<T> find(Predicate<? super T> predicate) {
        return Maybe.lazy(
            () -> Trampoline.evaluate(this,
                find -> sequence -> sequence.match(
                    (head, tail) -> predicate.test(head) ? terminate(Maybe.of(head)) : call(find, tail),
                    () -> terminate(Maybe.empty())
                )
            )
        );
    }

    /** The index at which an element first appears in this sequence, if it appears and the index fits in a long. */
    public Maybe<Long> index(T element) {
        requireNonNull(element);
        return Maybe.lazy(
            () -> Trampoline.evaluate(this, 0L,
                index -> sequence -> n -> n < 0 ? terminate(Maybe.empty()) : sequence.match(
                    (head, tail) -> head.equals(element) ? terminate(Maybe.of(n)) : call(index, tail, n + 1),
                    () -> terminate(Maybe.empty())
                )
            )
        );
    }

    /**
     * Combine the elements of this sequence into a single result, accumulating from the left.
     *
     * @param <R> the type of the result
     *
     * @see Functions#flip(BiFunction)
     */
    public <R> R foldLeft(R initial, BiFunction<R, ? super T, R> function) {
        return Trampoline.evaluate(this, initial,
            fold -> sequence -> result -> sequence.match(
                (head, tail) -> call(fold, tail, function.apply(result, head)),
                () -> terminate(result)
            )
        );
    }

    /**
     * Combine the elements of this sequence into a single result of the same type with a binary operator, accumulating
     * from the left, using the first element as the initial value if this sequence is non-empty.
     */
    public Maybe<T> foldLeft(BiFunction<T, T, T> operator) {
        return this.match((head, tail) -> tail.foldLeft(head, operator));
    }

    /**
     * Combine the elements of this sequence into a single result, accumulating from the right.
     *
     * @param <R> the type of the result
     */
    public <R> R foldRight(R initial, BiFunction<? super T, R, R> function) {
        return this.reverse().foldLeft(initial, flip(function));
    }

    /**
     * Combine the elements of this sequence into a single result of the same type with a binary operator, accumulating
     * from the right, using the last element as the initial value if this sequence is non-empty.
     */
    public Maybe<T> foldRight(BiFunction<T, T, T> operator) {
        return this.reverse().foldLeft(flip(operator));
    }

    /**
     * Combine the elements of this sequence into a single result, accumulating from the right, deferring evaluation of
     * partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences.
     */
    public <R> R foldRight(R initial, Function<? super T, ? extends Either<? extends R, ? extends Function<R, R>>> function) {
        return Trampoline.evaluate(this, Sequence.<Function<R, R>>empty(),
            fold -> sequence -> reversed -> sequence.match(
                (head, tail) -> function.apply(head).match(
                    left -> terminate(reversed.foldLeft(left, uncurry(Functions::apply))),
                    right -> call(fold, tail, Sequence.cons(right, reversed))
                ),
                () -> terminate(reversed.foldLeft(initial, uncurry(Functions::apply)))
            )
        );
    }

    /**
     * Combine the elements of this sequence into a single result of the same type, accumulating from the right, using
     * the last element as the initial value if this sequence is non-empty, deferring evaluation of partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences.
     */
    public Maybe<T> foldRight(Function<T, Either<T, Function<T, T>>> operator) {
        return Maybe.lazy(
            () -> Trampoline.evaluate(this, Sequence.<Function<T, T>>empty(),
                fold -> sequence -> reversed -> sequence.match(
                    (head, tail) -> tail.matchNonEmpty(
                        rest -> operator.apply(head).match(
                            left -> terminate(Maybe.of(() -> reversed.foldLeft(left, uncurry(Functions::apply)))),
                            right -> call(fold, rest, Sequence.cons(right, reversed))
                        ),
                        () -> terminate(Maybe.of(() -> reversed.foldLeft(head, uncurry(Functions::apply))))
                    ),
                    () -> terminate(Maybe.empty())
                )
            )
        );
    }

    /**
     * Lazily combine the elements of this sequence into a single result, accumulating from the right, deferring
     * evaluation of partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences. Unlike {@link Sequence#foldRight(Object, Function)}, this
     * method is not stack safe if the reducing function eagerly evaluates the partial results.
     */
    public <R> R foldRightLazy(R initial, BiFunction<? super T, Supplier<R>, R> function) {
        return this.match(
            (head, tail) -> function.apply(head, () -> tail.foldRightLazy(initial, function)),
            initial
        );
    }

    /**
     * Lazily combine the elements of this sequence into a single element, accumulating from the right, using the last
     * element as the initial value if this sequence is non-empty, deferring evaluation of partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences. Unlike {@link Sequence#foldRight(Function)}, this method is
     * not stack safe if the reducing function eagerly evaluates the partial results.
     */
    public Maybe<T> foldRightLazy(BiFunction<T, Supplier<T>, T> operator) {
        return this.match(
            (head, tail) -> tail.foldRightLazy(operator).matchLazy(
                result -> operator.apply(head, result),
                head
            )
        );
    }

    /** Transfer the elements of this sequence to a collection. */
    public <C extends Collection<? super T>> C collect(Supplier<C> collection) {
        return this.collect(collection, Collection::add);
    }

    /**
     * Combine the elements of this sequence into a single, mutable result, accumulating from the left.
     *
     * @see Sequence#collect(Collector)
     */
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator) {
        R result = supplier.get();
        this.forEach(Consumers.apply(accumulator, result));
        return result;
    }

    /**
     * Combine the elements of this sequence into a single, mutable result using a collector, accumulating from the
     * left.
     *
     * @see Sequence#collect(Supplier, BiConsumer)
     * @see Collectors
     */
    public <A, R> R collect(Collector<? super T, A, ? extends R> collector) {
        return collector.finisher().apply(this.collect(collector.supplier(), collector.accumulator()));
    }

    /**
     * Impose on this sequence a given order on its elements.
     *
     * @see Sequences#sort(Sequence)
     * @see Sequence#increasing(Comparator)
     * @see Sequence#strictlyIncreasing(Comparator)
     * @see Sequence#decreasing(Comparator)
     * @see Sequence#strictlyDecreasing(Comparator)
     */
    public Sequence<T> sort(Comparator<? super T> comparator) {
        return Sequence.lazy(
            () -> this.match(
                (head, tail) -> Sequences.concatenate(
                    tail.filter(lessThan(head, comparator)).sort(comparator),
                    Sequence.cons(head, tail.filter(greaterThanOrEqualTo(head, comparator)).sort(comparator))
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Impose on this sequence the natural order imposed on its elements by projecting them through a function.
     *
     * <p>{@code sequence.sort(function)} is equivalent to {@code sequence.sort(Comparator.comparing(function)}, but in
     * the former case, {@code function} is only invoked once per element, which is useful if it is expensive. The
     * trade-off is more indirection and object creation: each element must be mapped to a pair holding the element and
     * the result of the function, and then after sorting, the pairs must be mapped back to the elements.
     *
     * @see Sequence#sort(Comparator)
     * @see Sequence#sort(Function, Comparator)
     * @see Comparator#comparing(Function)
     */
    public <R extends Comparable<? super R>> Sequence<T> sort(Function<? super T, ? extends R> function) {
        return this.sort(function, naturalOrder());
    }

    /**
     * Impose on this sequence a given order imposed on its elements by projecting them through a function.
     *
     * <p>{@code sequence.sort(function,comparator)} is equivalent to
     * {@code sequence.sort(Comparator.comparing(function,comparator)}, but in the former case, {@code function} is
     * only invoked once per element, which is useful if it is expensive. The trade-off is more indirection and object
     * creation: each element must be mapped to a pair holding the element and the result of the function, and then
     * after sorting, the pairs must be mapped back to the elements.
     *
     * @see Sequence#sort(Comparator)
     * @see Sequence#sort(Function)
     * @see Comparator#comparing(Function, Comparator)
     */
    public <R> Sequence<T> sort(Function<? super T, ? extends R> function, Comparator<? super R> comparator) {
        return this.map(
            element -> Pair.of(element, function.apply(element))
        ).sort(
            bySecond(comparator)
        ).map(
            Pair::first
        );
    }

    /** Reverse the order of this sequence. */
    public Sequence<T> reverse() {
        return Sequence.lazy(
            () -> Trampoline.evaluate(this, Sequence.<T>empty(),
                reverse -> sequence -> reversed -> sequence.matchLazy(
                    (head, tail) -> call(reverse, tail, Sequence.cons(head, reversed)),
                    () -> terminate(reversed)
                )
            )
        );
    }

    /**
     * Concatenate this sequence with itself infinitely many times.
     *
     * @see Sequence#cycle(long)
     * @see Sequence#concatenate(Sequence)
     */
    public Sequence<T> cycle() {
        return Sequence.lazy(
            () -> this.matchNonEmpty(
                fix(cycle -> sequence -> sequence.concatenate(() -> cycle.apply(sequence))),
                Sequence.empty()
            )
        );
    }

    /**
     * Repeat this sequence {@code n} times.
     *
     * @see Sequence#cycle()
     * @see Sequence#concatenate(Sequence)
     */
    public Sequence<T> cycle(long n) {
        return n <= 0 ? Sequence.empty() : Sequence.lazy(
            () -> this.matchNonEmpty(
                sequence -> cycle(sequence, n),
                Sequence.empty()
            )
        );
    }

    private static <T> Sequence<T> cycle(Sequence<T> sequence, long n) {
        if (n == 1) {
            return sequence;
        } else {
            return sequence.concatenate(() -> cycle(sequence, n - 1));
        }
    }

    /**
     * Add an element to the end of this sequence infinitely many times.
     *
     * @see Sequence#append(Object)
     * @see Sequence#extend(Function)
     */
    public Sequence<T> pad(T padding) {
        return this.concatenate(Sequence.repeat(padding));
    }

    /**
     * Extend this sequence if non-empty by iteratively applying a function to the last element.
     *
     * @see Sequence#pad(Object)
     * @see Sequence#iterate(Object, Function)
     */
    public Sequence<T> extend(Function<T, T> function) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> tail.matchNonEmpty(
                    sequence -> Sequence.cons(head, sequence.extend(function)),
                    () -> Sequence.iterate(head.get(), function)
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Add an element to the end of this sequence.
     *
     * @see Sequence#concatenate(Sequence)
     */
    public Sequence<T> append(T element) {
        return this.concatenate(Sequence.of(element));
    }

    /**
     * A view of this sequence with an element added at the given index, shifting subsequent elements to the right.
     *
     * @see Sequence#insert(long, Sequence)
     * @see Sequence#append(Object)
     */
    public Sequence<T> insert(long index, T element) {
        return index < 0 ? this : index == 0 ? Sequence.cons(element, this) : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail.insert(index - 1, element)),
                Sequence.empty()
            )
        );
    }

    /**
     * A view of this sequence with the elements of another sequence added at the given index, shifting subsequent
     * elements of this sequence to the right.
     *
     * @see Sequence#insert(long, Object)
     * @see Sequence#concatenate(Sequence)
     */
    public Sequence<T> insert(long index, Sequence<? extends T> sequence) {
        return index <= 0 ? Sequences.concatenate(sequence.skip(Math.abs(index)), this) : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail.insert(index - 1, sequence)),
                Sequence.empty()
            )
        );
    }

    /** A view of this sequence with a given element inserted between each pair of adjacent elements. */
    public Sequence<T> intersperse(T element) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (x, xs) -> Sequence.cons(
                    x,
                    () -> let(
                        xs,
                        fix(
                            intersperse -> sequence -> sequence.matchLazy(
                                (head, tail) -> Sequence.cons(
                                    element,
                                    Sequence.cons(head, () -> intersperse.apply(tail))
                                ),
                                Sequence.empty()
                            )
                        )
                    )
                ),
                Sequence.empty()
            )
        );
    }

    /** Remove elements rejected by a predicate. */
    public Sequence<T> filter(Predicate<? super T> predicate) {
        return Sequence.lazy(
            () -> this.skipWhile(predicate.negate()).matchLazy(
                (head, tail) -> Sequence.cons(head, tail.filter(predicate)),
                Sequence.empty()
            )
        );
    }

    /**
     * Narrow the element type of this sequence, filtering out incompatible elements.
     *
     * @see Sequence#cast(Sequence)
     * @see Sequence#filter(Predicate)
     * @see Maybe#narrow(Class)
     */
    public <R extends T> Sequence<R> narrow(Class<? extends R> type) {
        return this.filter(type::isInstance).map(type::cast);
    }

    /**
     * A prefix of this sequence.
     *
     * @see Sequence#skip(long)
     * @see Sequence#slice(long, long)
     */
    public Sequence<T> take(long length) {
        return length < 1 ? Sequence.empty() : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail.take(length - 1)),
                Sequence.empty()
            )
        );
    }

    /** The longest prefix of this sequence such that every element satisfies a predicate. */
    public Sequence<T> takeWhile(Predicate<? super T> predicate) {
        return Sequence.lazy(
            () -> this.match(
                (head, tail) -> {
                    if (predicate.test(head)) {
                        return Sequence.cons(head, tail.takeWhile(predicate));
                    } else {
                        return Sequence.empty();
                    }
                },
                Sequence.empty()
            )
        );
    }

    /** Take as many elements of this sequence as another sequence produces. */
    public Sequence<T> takeWhile(Sequence<?> sequence) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (x, xs) -> sequence.matchLazy(
                    (y, ys) -> Sequence.cons(
                        x,
                        xs.takeWhile(ys)
                    ),
                    Sequence.empty()
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Remove a prefix of this sequence with a given length.
     *
     * @see Sequence#take(long)
     * @see Sequence#slice(long, long)
     */
    public Sequence<T> skip(long length) {
        return length <= 0 ? this : Sequence.lazy(
            () -> Trampoline.evaluate(this, length,
                skip -> sequence -> n -> n == 0 ? terminate(sequence) : sequence.matchLazy(
                    (head, tail) -> call(skip, tail, n - 1),
                    () -> terminate(Sequence.empty())
                )
            )
        );
    }

    /** The elements following the longest prefix of elements of this sequence that satisfy a predicate. */
    public Sequence<T> skipWhile(Predicate<? super T> predicate) {
        return Sequence.lazy(
            () -> Trampoline.evaluate(this,
                skip -> sequence -> sequence.match(
                    (head, tail) -> predicate.test(head) ? call(skip, tail) : terminate(Sequence.cons(head, tail)),
                    () -> terminate(Sequence.empty())
                )
            )
        );
    }

    /** Skip as many elements of this sequence as another sequence produces. */
    public Sequence<T> skipWhile(Sequence<?> sequence) {
        return Sequence.lazy(
            () -> Trampoline.evaluate(this, sequence,
                skip -> seq -> other -> other.matchLazy(
                    (y, ys) -> seq.matchLazy(
                        (x, xs) -> call(skip, xs, ys),
                        () -> terminate(Sequence.empty())
                    ),
                    () -> terminate(seq)
                )
            )
        );
    }

    /**
     * Every {@code n}th element of this sequence, starting with the head.
     *
     * <p>If this sequence is empty, the resulting sequence is empty.
     */
    public Sequence<T> step(long n) {
        if (n <= 0) {
            return this.take(1);
        } else if (n == 1) {
            return this;
        } else {
            return Sequence.lazy(
                () -> this.matchLazy(
                    (head, tail) -> Sequence.cons(head, tail.skip(n - 1).step(n)),
                    Sequence.empty()
                )
            );
        }
    }

    /**
     * A contiguous subsequence defined by an inclusive starting index and an exclusive ending index.
     *
     * @see Sequence#take(long)
     * @see Sequence#skip(long)
     * @see Sequence#delete(long)
     * @see Sequence#sliceLength(long, long)
     */
    public Sequence<T> slice(long from, long to) {
        return this.take(to).skip(from);
    }

    /**
     * A contiguous subsequence defined by an inclusive starting index and a length.
     *
     * @see Sequence#take(long)
     * @see Sequence#skip(long)
     * @see Sequence#deleteLength(long, long)
     * @see Sequence#slice(long, long)
     */
    public Sequence<T> sliceLength(long from, long length) {
        return this.skip(from).take(length);
    }

    /**
     * Remove the element at a given index in this sequence.
     *
     * @see Sequence#insert(long, Object)
     * @see Sequence#delete(long, long)
     */
    public Sequence<T> delete(long index) {
        return index < 0 ? this : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> index == 0 ? tail : Sequence.cons(head, tail.delete(index - 1)),
                Sequence.empty()
            )
        );
    }

    /**
     * Remove a contiguous subsequence from this sequence defined by an inclusive starting index and an exclusive ending
     * index.
     *
     * @see Sequence#take(long)
     * @see Sequence#skip(long)
     * @see Sequence#slice(long, long)
     * @see Sequence#deleteLength(long, long)
     */
    public Sequence<T> delete(long from, long to) {
        return to <= from ? this : from <= 0 ? this.skip(to) : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail.delete(from - 1, to - 1)),
                Sequence.empty()
            )
        );
    }

    /** Filter out the longest prefix of another sequence that is a subsequence of this sequence. */
    public Sequence<T> delete(Sequence<? extends T> sequence) {
        return Sequence.lazy(
            () -> Trampoline.evaluate(this, sequence,
                delete -> xs_ -> ys_ -> ys_.matchLazy(
                    (y, ys) -> xs_.match(
                        (x, xs) -> let(
                            y.get(),
                            y_ -> x.equals(y_) ? call(delete, xs, ys) : terminate(
                                Sequence.cons(x, xs.delete(Sequence.cons(y_, ys)))
                            )
                        ),
                        () -> terminate(Sequence.empty())
                    ),
                    terminate(xs_)
                )
            )
        );
    }

    /**
     * Remove a contiguous subsequence from this sequence defined by an inclusive starting index and a length.
     *
     * @see Sequence#take(long)
     * @see Sequence#skip(long)
     * @see Sequence#sliceLength(long, long)
     * @see Sequence#delete(long, long)
     */
    public Sequence<T> deleteLength(long from, long length) {
        return this.delete(from, from + length);
    }

    /**
     * A sequence in which each distinct element of this sequence appears exactly once.
     *
     * @see Sequence#pairwiseDistinct()
     * @see Sequences#deduplicate(Sequence)
     * @see Sequence#deduplicate(Function)
     * @see Sequence#deduplicate(Comparator)
     */
    public Sequence<T> deduplicate() {
        return Sequence.lazy(() -> this.filter(new HashSet<>()::add).memoize());
    }

    /**
     * A sequence in which each element of this sequence that is distinct as viewed through a function appears exactly
     * once.
     *
     * @see Sequence#pairwiseDistinct(Function)
     * @see Sequence#group(Function)
     * @see Sequence#deduplicate()
     * @see Sequence#deduplicate(Comparator)
     * @see Sequences#deduplicate(Sequence)
     */
    public Sequence<T> deduplicate(Function<? super T, ?> function) {
        return Sequence.lazy(() -> this.filter(Predicates.compose(new HashSet<>()::add, function)).memoize());
    }

    /**
     * A sequence in which each element of this sequence that is considered distinct by a comparator appears exactly
     * once.
     *
     * @see Sequence#pairwiseDistinct(Comparator)
     * @see Sequence#deduplicate()
     * @see Sequence#deduplicate(Function)
     * @see Sequences#deduplicate(Sequence)
     * @see Sequences#group(Sequence, Comparator)
     */
    public Sequence<T> deduplicate(Comparator<? super T> comparator) {
        return Sequence.lazy(() -> this.filter(new TreeSet<>(comparator)::add).memoize());
    }

    /**
     * The elements of this sequence excluding the head, if this sequence is non-empty.
     *
     * @see Sequence#head()
     * @see Sequence#last()
     * @see Sequence#initial()
     * @see Sequence#uncons()
     */
    public Maybe<Sequence<T>> tail() {
        return this.matchLazy((head, tail) -> tail);
    }

    /**
     * The elements of this sequence excluding the last element, if this sequence is non-empty.
     *
     * @see Sequence#head()
     * @see Sequence#last()
     * @see Sequence#tail()
     * @see Sequence#uncons()
     */
    public Maybe<Sequence<T>> initial() {
        return this.matchLazy(
            (head, tail) -> Sequence.lazy(
                () -> tail.initial().map(Functions.apply(Sequence::cons, head)).or(Sequence.empty())
            )
        );
    }

    /** A view of this sequence with the given element at the given index instead of the original element. */
    public Sequence<T> replace(long index, T element) {
        return index < 0 ? this : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> index == 0 ? Sequence.cons(element, tail) : Sequence.cons(
                    head,
                    tail.replace(index - 1, element)
                ),
                Sequence.empty()
            )
        );
    }

    /** A view of this sequence with a subsequence starting at the given index replaced by the given sequence. */
    public Sequence<T> replace(long index, Sequence<? extends T> sequence) {
        return index < 0 ? this.replace(0, sequence.skip(Math.abs(index))) : Sequence.lazy(
            () -> this.matchLazy(
                (x, xs) -> {
                    if (index == 0) {
                        return sequence.matchLazy(
                            (y, ys) -> Sequence.cons(y, xs.replace(0, ys)),
                            () -> Sequence.cons(x, xs)
                        );
                    } else {
                        return Sequence.cons(x, xs.replace(index - 1, sequence));
                    }
                },
                Sequence.empty()
            )
        );
    }

    /** A view of this sequence with the element at the given index projected through a function. */
    public Sequence<T> update(long index, Function<? super T, ? extends T> function) {
        return index < 0 ? this : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> {
                    if (index == 0) {
                        return Sequence.cons(() -> function.apply(head.get()), tail);
                    } else {
                        return Sequence.cons(head, tail.update(index - 1, function));
                    }
                },
                Sequence.empty()
            )
        );
    }

    /**
     * Apply a function to each element of this sequence.
     *
     * @see Sequence#flatMap(Function)
     * @see Sequence#apply(Sequence)
     */
    public <R> Sequence<R> map(Function<? super T, ? extends R> function) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(() -> function.apply(head.get()), tail.map(function)),
                Sequence.empty()
            )
        );
    }

    /**
     * Map each element of this sequence to a sequence and concatenate the results.
     *
     * @see Sequence#map(Function)
     * @see Sequence#apply(Sequence)
     * @see Sequences#concatenate(Sequence)
     */
    public <R> Sequence<R> flatMap(Function<? super T, ? extends Sequence<? extends R>> function) {
        return Sequences.concatenate(this.map(function));
    }

    /**
     * Apply each of the given functions to every element of this sequence.
     *
     * <p>This is the
     * <a href="http://hackage.haskell.org/packages/archive/base/latest/doc/html/Control-Applicative.html#v:-60--42--62-">{@code <*>}</a>
     * operator from Haskell, specialized to sequences, with its operands flipped.
     *
     * @see Sequence#map(Function)
     * @see Sequence#flatMap(Function)
     * @see Sequence#zip(Sequence)
     * @see Sequences#apply(Sequence, Sequence)
     */
    public <R> Sequence<R> apply(Sequence<? extends Function<? super T, ? extends R>> functions) {
        return functions.flatMap(this::map);
    }

    /**
     * Binary function application lifted to sequences.
     *
     * <p>Map over the Cartesian product of this sequence and another sequence.
     *
     * @see Sequences#product(Sequence)
     * @see Sequence#apply(Sequence, BiPredicate, BiFunction)
     * @see Sequence#map(Function)
     * @see Sequence#zip(Sequence)
     */
    public <U, R> Sequence<R> apply(Sequence<? extends U> sequence, BiFunction<? super T, ? super U, ? extends R> function) {
        return sequence.apply(this.map(curry(function)));
    }

    /**
     * Filter and map over the Cartesian product of this sequence and another sequence.
     *
     * @see Sequences#product(Sequence)
     * @see Sequence#filter(Predicate)
     * @see Sequence#map(Function)
     */
    public <U, R> Sequence<R> apply(Sequence<? extends U> sequence, BiPredicate<? super T, ? super U> predicate, BiFunction<? super T, ? super U, ? extends R> function) {
        return this.flatMap(
            element -> sequence.filter(Predicates.apply(predicate, element)).map(Functions.apply(function, element))
        );
    }

    /**
     * Apply each function in the given sequence to the corresponding element of this sequence, truncating whichever
     * sequence is longer.
     *
     * <p>This is an alternative definition of the Haskell operator
     * <a href="https://hackage.haskell.org/package/base/docs/Control-Applicative.html">{@code <*>}</a> for sequences
     * based on zipping, rather than the Cartesian product as in {@link Sequence#apply(Sequence)}.
     *
     * @see Sequence#zip(Sequence, BiFunction)
     * @see Sequence#zip(Sequence, BiPredicate, BiFunction)
     * @see <a href="http://hackage.haskell.org/packages/archive/base/latest/doc/html/Control-Applicative.html#t:ZipList">ZipList</a>
     */
    public <R> Sequence<R> zip(Sequence<? extends Function<? super T, ? extends R>> functions) {
        return this.zip(functions, flip(Function::apply));
    }

    /**
     * Zip this sequence with another sequence, mapping over the pairs of elements.
     *
     * @see Sequence#zip(Sequence)
     * @see Sequence#map(Function)
     * @see Sequence#zip(BiFunction)
     */
    public <U, R> Sequence<R> zip(Sequence<? extends U> sequence, BiFunction<? super T, ? super U, ? extends R> function) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (x, xs) -> sequence.matchLazy(
                    (y, ys) -> Sequence.cons(
                        () -> function.apply(x.get(), y.get()),
                        xs.zip(ys, function)
                    ),
                    Sequence.empty()
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Zip this sequence with another sequence, filtering and mapping over the pairs of elements.
     *
     * @see Sequence#zip(Sequence)
     * @see Sequence#filter(Predicate)
     * @see Sequence#map(Function)
     */
    public <U, R> Sequence<R> zip(Sequence<? extends U> sequence, BiPredicate<? super T, ? super U> predicate, BiFunction<? super T, ? super U, ? extends R> function) {
        return Sequences.zip(this, sequence).filter(gather(predicate)).map(gather(function));
    }

    /**
     * Zip this sequence with its tail (i.e., view this sequence through a sliding window, two elements at a time).
     *
     * <p>The resulting sequence is empty if this sequence is empty. Otherwise, the
     * length of the resulting sequence is one less than the length of this sequence.
     *
     * @see Sequences#zip(Sequence, Sequence)
     * @see Sequence#zip(Sequence)
     * @see Sequence#zip(BiFunction)
     * @see Sequence#zip(BiPredicate, BiFunction)
     * @see Sequence#slide(long)
     * @see Sequence#slide(long, long)
     */
    public Sequence<Pair<T, T>> zip() {
        return this.zip(Pair::of);
    }

    /**
     * Zip this sequence with its tail, mapping over the pairs.
     *
     * @see Sequence#zip(Sequence)
     * @see Sequence#zip(Sequence, BiPredicate, BiFunction)
     * @see Sequence#slide(long)
     * @see Sequence#slide(long, long)
     */
    public <R> Sequence<R> zip(BiFunction<? super T, ? super T, ? extends R> function) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail).zip(tail, function),
                Sequence.empty()
            )
        );
    }

    /**
     * Zip this sequence with its tail, filtering and mapping over the pairs.
     *
     * @see Sequence#zip(Sequence)
     * @see Sequence#zip(Sequence, BiPredicate, BiFunction)
     * @see Sequence#slide(long, long)
     */
    public <R> Sequence<R> zip(BiPredicate<? super T, ? super T> predicate, BiFunction<? super T, ? super T, ? extends R> function) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail).zip(tail, predicate, function),
                Sequence.empty()
            )
        );
    }

    /**
     * Zip the natural numbers with the elements of this sequence.
     *
     * <p>The resulting sequence is the same length as this sequence.
     *
     * @see Sequence#zip(Sequence)
     * @see Sequence#index(BiFunction)
     * @see Sequence#index(BiPredicate, BiFunction)
     * @see Sequence#indices()
     * @see Sequences#longs()
     */
    public Sequence<Pair<Long, T>> index() {
        return Sequences.zip(Sequences.longs(), this);
    }

    /**
     * Zip the natural numbers with the elements of this sequence, mapping over the pairs.
     *
     * <p>The resulting sequence is the same length as this sequence.
     *
     * @see Sequence#zip(Sequence,BiFunction)
     * @see Sequence#index()
     * @see Sequence#index(BiPredicate, BiFunction)
     * @see Sequence#indices()
     * @see Sequences#longs()
     */
    public <R> Sequence<R> index(BiFunction<? super Long, ? super T, ? extends R> function) {
        return Sequences.longs().zip(this, function);
    }

    /**
     * Zip the natural numbers with the elements of this sequence, filtering and mapping over the pairs.
     *
     * <p>The resulting sequence is no longer than this sequence.
     *
     * @see Sequence#zip(Sequence, BiPredicate, BiFunction)
     * @see Sequence#index()
     * @see Sequence#index(BiFunction)
     * @see Sequence#indices()
     * @see Sequences#longs()
     */
    public <R> Sequence<R> index(BiPredicate<? super Long, ? super T> predicate, BiFunction<? super Long, ? super T, ? extends R> function) {
        return Sequences.longs().zip(this, predicate, function);
    }

    /**
     * The prefix of the natural numbers that represent valid indices of this sequence.
     *
     * <p>The resulting sequence is the same length as this sequence.
     *
     * @see Sequences#longs()
     */
    public Sequence<Long> indices() {
        return Sequences.longs().takeWhile(this);
    }

    /**
     * Alternately draw elements from this sequence and another sequence.
     *
     * @see Sequence#concatenate(Sequence)
     * @see Sequences#interleave(Sequence, Sequence)
     * @see Sequences#interleave(Sequence[])
     * @see Sequences#interleave(Sequence)
     */
    public Sequence<T> interleave(Sequence<? extends T> sequence) {
        return Sequences.concatenate(this.zip(sequence, Sequence::of));
    }

    /**
     * Lazily append to this sequence every element of another sequence.
     *
     * @see Sequence#append(Object)
     * @see Sequence#insert(long, Sequence)
     * @see Sequence#interleave(Sequence)
     * @see Sequence#concatenate(Supplier)
     * @see Sequences#concatenate(Sequence, Sequence)
     * @see Sequences#concatenate(Sequence[])
     * @see Sequences#concatenate(Sequence)
     */
    public Sequence<T> concatenate(Sequence<? extends T> sequence) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail.concatenate(sequence)),
                sequence
            )
        );
    }

    /**
     * Lazily append to this sequence every element of another, lazily evaluated sequence.
     *
     * @see Sequence#concatenate(Sequence)
     * @see Sequences#concatenate(Sequence, Sequence)
     * @see Sequences#concatenate(Sequence[])
     * @see Sequences#concatenate(Sequence)
     */
    public Sequence<T> concatenate(Supplier<? extends Sequence<? extends T>> sequence) {
        requireNonNull(sequence);
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(head, tail.concatenate(sequence)),
                sequence
            )
        );
    }

    /** Each element of this sequence paired with the subsequence excluding that element. */
    public Sequence<Pair<T, Sequence<T>>> select() {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(
                    () -> Pair.of(head.get(), tail),
                    tail.select().map(
                        Functions.gather(
                            (selection, rest) -> Pair.of(selection, Sequence.cons(head, rest))
                        )
                    )
                ),
                Sequence.empty()
            )
        );
    }

    /** Split this sequence into two sequences by splitting each element into two elements. */
    public <U, V> Pair<Sequence<U>, Sequence<V>> unzip(Function<? super T, ? extends U> first, Function<? super T, ? extends V> second) {
        return Pair.lazy(
            () -> let(this.memoize(), sequence -> Pair.of(sequence.map(first), sequence.map(second)))
        );
    }

    /** The sequence of partial results of a left fold over this sequence. */
    public <R> Sequence<R> scanLeft(R initial, BiFunction<R, ? super T, R> function) {
        return Sequence.cons(
            initial,
            () -> this.match(
                (head, tail) -> tail.scanLeft(function.apply(initial, head), function),
                Sequence.empty()
            )
        );
    }

    /**
     * The sequence of partial results of a left fold over this sequence, using the first element as the initial value.
     */
    public Sequence<T> scanLeft(BiFunction<T, T, T> operator) {
        return Sequence.lazy(
            () -> this.match(
                (head, tail) -> tail.scanLeft(head, operator),
                Sequence.empty()
            )
        );
    }

    /** The sequence of partial results of a right fold over this sequence. */
    public <R> Sequence<R> scanRight(R initial, BiFunction<? super T, R, R> function) {
        return this.reverse().scanLeft(initial, flip(function)).reverse();
    }

    /**
     * The sequence of partial results of a right fold over this sequence, using the last element as the initial value.
     *
     * @see Sequence#foldRight(BiFunction)
     * @see Sequence#scanRight(Object, BiFunction)
     * @see Sequence#scanLeft(BiFunction)
     */
    public Sequence<T> scanRight(BiFunction<T, T, T> operator) {
        return this.reverse().scanLeft(flip(operator)).reverse();
    }

    /**
     * The sequence of partial results of a right fold over this sequence, deferring evaluation of the partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences.
     */
    public <R> Sequence<R> scanRight(R initial, Function<? super T, ? extends Either<? extends R, ? extends Function<R, R>>> function) {
        return Sequence.lazy(
            () -> Trampoline.evaluate(this, Sequence.<Function<R, R>>empty(),
                scan -> sequence -> reversed -> sequence.match(
                    (head, tail) -> function.apply(head).match(
                        left -> terminate(
                            Sequences.concatenate(
                                reversed.scanLeft(left, uncurry(Functions::apply)).reverse(),
                                tail.scanRight(initial, function)
                            )
                        ),
                        right -> call(scan, tail, Sequence.cons(right, reversed))
                    ),
                    () -> terminate(reversed.scanLeft(initial, uncurry(Functions::apply)).reverse())
                )
            )
        );
    }

    /**
     * The sequence of partial results of a right fold over this sequence, using the last element as the initial value
     * if this sequence is non-empty, deferring evaluation of the partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences.
     */
    public Sequence<T> scanRight(Function<T, Either<T, Function<T, T>>> operator) {
        return Sequence.lazy(
            () -> Trampoline.evaluate(this, Sequence.<Function<T, T>>empty(),
                scan -> sequence -> reversed -> sequence.match(
                    (head, tail) -> tail.matchNonEmpty(
                        rest -> operator.apply(head).match(
                            left -> terminate(
                                Sequences.concatenate(
                                    reversed.scanLeft(left, uncurry(Functions::apply)).reverse(),
                                    rest.scanRight(operator)
                                )
                            ),
                            right -> call(scan, rest, Sequence.cons(right, reversed))
                        ),
                        () -> terminate(reversed.scanLeft(head, uncurry(Functions::apply)).reverse())
                    ),
                    () -> terminate(Sequence.empty())
                )
            )
        );
    }

    /**
     * The sequence of partial results of a lazy right fold over this sequence, deferring evaluation of the partial
     * results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences. Unlike {@link Sequence#scanRight(Object, Function)}, this
     * method is not stack safe if the reducing function eagerly evaluates the partial results.
     */
    public <R> Sequence<R> scanRightLazy(R initial, BiFunction<? super T, Supplier<R>, R> function) {
        return Sequence.lazy(
            () -> this.foldRightLazy(
                Pair.of(initial, Sequence.<R>empty()),
                (element, results) -> let(
                    Functions.memoize(results),
                    rest -> Pair.of(
                        function.apply(element, () -> rest.get().first()),
                        () -> rest.get().matchLazy(Sequence::cons)
                    )
                )
            ).match(Sequence::cons)
        );
    }

    /**
     * The sequence of partial results of a lazy right fold over this sequence, using the last element as the initial
     * value if this sequence is non-empty, deferring evaluation of the partial results.
     *
     * <p>This can return without evaluating the entire sequence if the reducing function does not evaluate the partial
     * result, allowing it to work on infinite sequences. Unlike {@link Sequence#scanRight(Function)}, this method is
     * not stack safe if the reducing function eagerly evaluates the partial results.
     */
    public Sequence<T> scanRightLazy(BiFunction<T, Supplier<T>, T> operator) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (x, xs) -> let(
                    xs.scanRightLazy(operator).memoize(),
                    results -> Sequence.cons(
                        () -> results.matchLazy((y, ys) -> operator.apply(x.get(), y), x),
                        results
                    )
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Split this sequence into the elements that satisfy a predicate and those that are rejected.
     *
     * @see Sequence#filter(Predicate)
     * @see Sequence#span(Predicate)
     * @see Sequence#group(Function)
     */
    public Pair<Sequence<T>, Sequence<T>> partition(Predicate<? super T> predicate) {
        return Pair.lazy(
            () -> let(
                this.memoize(),
                sequence -> Pair.of(sequence.filter(predicate), sequence.filter(predicate.negate()))
            )
        );
    }

    /**
     * Split this sequence into the longest prefix such that every element satisfies a predicate and the suffix that
     * follows.
     *
     * <p>Equivalent to {@code Pair.of(this.takeWhile(predicate), this.skipWhile(predicate))}.
     *
     * @see Sequence#span(long)
     * @see Sequence#partition(Predicate)
     * @see Sequence#group(Function)
     * @see Sequence#takeWhile(Predicate)
     * @see Sequence#skipWhile(Predicate)
     */
    public Pair<Sequence<T>, Sequence<T>> span(Predicate<? super T> predicate) {
        return Pair.lazy(
            () -> let(
                this.memoize(),
                sequence -> Pair.of(sequence.takeWhile(predicate), sequence.skipWhile(predicate))
            )
        );
    }

    /**
     * Split this sequence into the prefix of a given length and the suffix that follows.
     *
     * <p>{@code this.span(length)} is equivalent to {@code Pair.of(this.take(length), this.skip(length))}.
     *
     * @see Sequence#span(Predicate)
     * @see Sequence#take(long)
     * @see Sequence#skip(long)
     */
    public Pair<Sequence<T>, Sequence<T>> span(long length) {
        return Pair.lazy(
            () -> let(
                this.memoize(),
                sequence -> Pair.of(sequence.take(length), sequence.skip(length))
            )
        );
    }

    /**
     * View this sequence through a moving window of a given size, advancing the window one position at a time.
     *
     * @see Sequence#slide(long,long)
     */
    public Sequence<Sequence<T>> slide(long window) {
        return this.slide(window, 1);
    }

    /**
     * View this sequence through a moving window of a given size, advancing a given number of steps each time.
     *
     * @see Sequence#slide(long)
     */
    public Sequence<Sequence<T>> slide(long window, long step) {
        return window < 1 ? Sequence.empty() : Sequence.lazy(
            () -> this.matchNonEmpty(
                sequence -> Sequence.cons(
                    sequence.take(window),
                    sequence.skip(step).slide(window, step)
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Split this sequence into non-empty, adjacent, contiguous subsequences of a given length.
     *
     * <p>The last subsequence may be shorter than the rest. Given a positive length,
     * {@link Sequences#concatenate(Sequence) concatenating} the result produces the original sequence.
     *
     * @see Sequence#group()
     * @see Sequence#group(BiPredicate)
     */
    public Sequence<Sequence<T>> group(long length) {
        return this.slide(length, length);
    }

    /**
     * Split this sequence into runs of equal elements.
     *
     * @see Sequence#group(BiPredicate)
     * @see Sequence#group(long)
     */
    public Sequence<Sequence<T>> group() {
        return this.group(Object::equals);
    }

    /**
     * Split this sequence into runs of elements such that pairs of adjacent elements satisfy a binary predicate.
     *
     * @see Sequence#group()
     * @see Sequence#group(long)
     */
    public Sequence<Sequence<T>> group(BiPredicate<? super T, ? super T> predicate) {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> tail.span(element -> predicate.test(head.get(), element)).match(
                    (equal, rest) -> Sequence.cons(
                        Sequence.cons(head, equal),
                        rest.group(predicate)
                    )
                ),
                Sequence.empty()
            )
        );
    }

    /**
     * Split this sequence into runs of elements that are equivalent when projected through a function.
     *
     * @see Sequence#group()
     * @see Sequence#group(long)
     * @see Sequence#group(BiPredicate)
     * @see Sequences#group(Sequence)
     * @see Sequences#group(Sequence, Comparator)
     */
    public Sequence<Sequence<T>> group(Function<? super T, ?> function) {
        return this.group(on(Object::equals, function));
    }

    /**
     * Every prefix of this sequence.
     *
     * @see Sequence#initial()
     * @see Sequence#take(long)
     * @see Sequence#hasPrefix(Sequence)
     * @see Sequence#suffixes()
     * @see Sequence#infixes()
     * @see Sequence#subsequences()
     */
    public Sequence<Sequence<T>> prefixes() {
        var sequence = this.memoize();
        return Sequence.cons(Sequence.empty(), sequence.indices().map(index -> sequence.take(index + 1)));
    }

    /**
     * Every suffix of this sequence.
     *
     * @see Sequence#tail()
     * @see Sequence#skip(long)
     * @see Sequence#hasSuffix(Sequence)
     * @see Sequence#prefixes()
     * @see Sequence#infixes()
     * @see Sequence#subsequences()
     */
    public Sequence<Sequence<T>> suffixes() {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequence.cons(Sequence.cons(head, tail), tail.suffixes()),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    /**
     * Every contiguous subsequence of this sequence.
     *
     * @see Sequence#slice(long, long)
     * @see Sequence#slide(long)
     * @see Sequence#hasInfix(Sequence)
     * @see Sequence#prefixes()
     * @see Sequence#suffixes()
     * @see Sequence#subsequences()
     */
    public Sequence<Sequence<T>> infixes() {
        return Sequence.cons(
            Sequence.empty(),
            this.suffixes().flatMap(suffix -> suffix.indices().map(index -> suffix.take(index + 1)))
        );
    }

    /**
     * Every sequence derived by removing zero or more elements of this sequence.
     *
     * @see Sequence#step(long)
     * @see Sequence#combinations(long)
     * @see Sequence#hasSubsequence(Sequence)
     * @see Sequence#prefixes()
     * @see Sequence#suffixes()
     * @see Sequence#infixes()
     */
    public Sequence<Sequence<T>> subsequences() {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> let(
                    tail.subsequences(),
                    subsequences -> subsequences.map(Functions.apply(Sequence::cons, head)).concatenate(subsequences)
                ),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    /**
     * Every sequence derived by concatenating zero or more subsequences of this sequence.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Kleene_star">Kleene star</a>
     */
    public Sequence<Sequence<T>> sequences() {
        return Sequence.lazy(
            () -> this.matchNonEmpty(
                sequence -> Sequences.concatenate(
                    Sequence.iterate(
                        Sequence.of(Sequence.empty()),
                        sequences -> sequence.apply(sequences, Sequence::cons)
                    )
                ),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    /**
     * Every subsequence of this sequence with a given length.
     *
     * <p>The number of {@code k}-combinations of a sequence of length {@code n} is the
     * <a href="https://en.wikipedia.org/wiki/Binomial_coefficient">binomial coefficient</a>,
     * {@code n} choose {@code k}.
     *
     * @see Sequence#subsequences()
     */
    public Sequence<Sequence<T>> combinations(long length) {
        return length < 0 ? Sequence.empty() : length == 0 ? Sequence.of(Sequence.empty()) : Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> Sequences.concatenate(
                    tail.combinations(length - 1).map(Functions.apply(Sequence::cons, head)),
                    tail.combinations(length)
                ),
                Sequence.empty()
            )
        );
    }

    /** Every ordering of the elements of this sequence. */
    public Sequence<Sequence<T>> permutations() {
        return Sequence.lazy(
            () -> this.matchNonEmpty(
                sequence -> sequence.select().flatMap(
                    Functions.gather(
                        (selection, rest) -> rest.permutations().map(Functions.apply(Sequence::cons, selection))
                    )
                ),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    /** Every equivalence relation on the elements of this sequence. */
    public Sequence<Sequence<Sequence<T>>> partitions() {
        return Sequence.lazy(
            () -> this.matchLazy(
                (head, tail) -> tail.partitions().flatMap(
                    partition -> Sequence.partitions(head, partition)
                ),
                () -> Sequence.of(Sequence.empty())
            )
        );
    }

    private static <T> Sequence<Sequence<Sequence<T>>> partitions(Supplier<T> element, Sequence<Sequence<T>> partition) {
        return Sequence.lazy(
            () -> partition.match(
                (cell, cells) -> Sequence.cons(
                    Sequence.cons(Sequence.cons(element, cell), cells),
                    Sequence.partitions(element, cells).map(Functions.apply(Sequence::cons, cell))
                ),
                () -> Sequence.of(Sequence.of(Sequence.of(element)))
            )
        );
    }
}
