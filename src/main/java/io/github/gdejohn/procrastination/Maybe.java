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
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.gdejohn.procrastination.Functions.on;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.Objects.requireNonNull;

/**
 * A lazily evaluated, persistent collection with at most one element.
 *
 * <p>{@code Maybe} is a lazy alternative to {@link Optional}. It implements {@link Iterable}, so it can be used in
 * for-each loops. An instance of {@code Maybe<T>} can be thought of as a value of type {@code T} that may or may not
 * exist, or as a {@link Sequence sequence} with a length of at most one, or as an {@code Either<Unit, T>} (i.e., the
 * sum of {@code T} and {@link Unit}).
 *
 * <p>{@code Maybe} is often used to model the possibility of failure. {@link Either} offers an alternative where the
 * failure case can have information associated with it (e.g., an exception, or an error message).
 *
 * @param <T> the type of the value, if it exists
 */
public abstract class Maybe<T> implements Iterable<T> {
    private static abstract class Proxy<T> extends Maybe<T> {
        protected Proxy() {}

        protected abstract Maybe<T> principal();

        @Override
        public <R> R match(Function<? super T, ? extends R> function, Supplier<? extends R> otherwise) {
            return this.principal().match(function, otherwise);
        }

        @Override
        public <R> R match(Function<? super T, ? extends R> function, R otherwise) {
            return this.principal().match(function, otherwise);
        }

        @Override
        public <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, Supplier<? extends R> otherwise) {
            return this.principal().matchLazy(function, otherwise);
        }

        @Override
        public <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, R otherwise) {
            return this.principal().matchLazy(function, otherwise);
        }

        @Override
        public Maybe<T> eager() {
            return this.principal().eager();
        }

        @Override
        public <X extends Throwable> T orThrow(Supplier<X> exception) throws X {
            return this.principal().orThrow(exception);
        }
    }

    private static final Maybe<?> EMPTY = new Maybe<>() {
        @Override
        public <R> R match(Function<? super Object, ? extends R> function, Supplier<? extends R> otherwise) {
            return otherwise.get();
        }

        @Override
        public <R> R match(Function<? super Object, ? extends R> function, R otherwise) {
            return otherwise;
        }

        @Override
        public <R> R matchLazy(Function<? super Supplier<Object>, ? extends R> function, Supplier<? extends R> otherwise) {
            return otherwise.get();
        }

        @Override
        public <R> R matchLazy(Function<? super Supplier<Object>, ? extends R> function, R otherwise) {
            return otherwise;
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

        @Override
        public Sequence<Object> sequence() {
            return Sequence.empty();
        }

        @Override
        public <X extends Throwable> Object orThrow(Supplier<X> exception) throws X {
            throw exception.get();
        }
    };

    private Maybe() {}

    /**
     * A lazily evaluated {@code Maybe}.
     *
     * <p>This is useful for deferring the decision as to whether or not a {@code Maybe} is empty.
     */
    public static <T> Maybe<T> lazy(Supplier<? extends Maybe<? extends T>> maybe) {
        requireNonNull(maybe);
        return new Proxy<>() {
            @Override
            protected Maybe<T> principal() {
                return cast(maybe.get().memoize());
            }

            @Override
            public Maybe<T> memoize() {
                return Maybe.memoize(this);
            }
        };
    }

    private static <T> Maybe<T> memoize(Proxy<T> maybe) {
        var principal = Functions.memoize(maybe::principal);
        return new Proxy<>() {
            @Override
            protected Maybe<T> principal() {
                return principal.get();
            }
        };
    }

    /**
     * The empty {@code Maybe}.
     *
     * <p>Analogous to the empty sequence, or the unit value.
     *
     * @see Maybe#of(Object)
     * @see Maybe#of(Supplier)
     * @see Maybe#lazy(Supplier)
     * @see Sequence#empty()
     * @see Unit#unit()
     */
    public static <T> Maybe<T> empty() {
        @SuppressWarnings("unchecked") // safe because Maybe.EMPTY never produces a value
        var empty = (Maybe<T>) Maybe.EMPTY;
        return empty;
    }

    /** Wrap an eagerly evaluated non-null value. */
    public static <T> Maybe<T> of(T value) {
        requireNonNull(value);
        return new Maybe<>() {
            @Override
            public <R> R match(Function<? super T, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(value);
            }

            @Override
            public <R> R match(Function<? super T, ? extends R> function, R otherwise) {
                return function.apply(value);
            }

            @Override
            public <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply((Supplier<T>) () -> value);
            }

            @Override
            public <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, R otherwise) {
                return function.apply((Supplier<T>) () -> value);
            }

            @Override
            public Sequence<T> sequence() {
                return Sequence.of(value);
            }

            @Override
            public <X extends Throwable> T orThrow(Supplier<X> exception) {
                return value;
            }
        };
    }

    /** Wrap a lazily evaluated non-null value. */
    public static <T> Maybe<T> of(Supplier<? extends T> value) {
        requireNonNull(value);
        return new Maybe<>() {
            @Override
            public <R> R match(Function<? super T, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(requireNonNull(value.get()));
            }

            @Override
            public <R> R match(Function<? super T, ? extends R> function, R otherwise) {
                return function.apply(requireNonNull(value.get()));
            }

            @Override
            public <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, Supplier<? extends R> otherwise) {
                return function.apply(Functions.memoize(value));
            }

            @Override
            public <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, R otherwise) {
                return function.apply(Functions.memoize(value));
            }

            @Override
            public Maybe<T> memoize() {
                Supplier<? extends T> memoized = Functions.memoize(value);
                return memoized == value ? this : Maybe.of(memoized);
            }

            @Override
            public Maybe<T> eager() {
                return Maybe.of(value.get());
            }

            @Override
            public <X extends Throwable> T orThrow(Supplier<X> exception) {
                return requireNonNull(value.get());
            }
        };
    }

    /** Wrap an eagerly evaluated nullable value. */
    public static <T> Maybe<T> nullable(T value) {
        return value == null ? Maybe.empty() : Maybe.of(value);
    }

    /** Wrap a lazily evaluated nullable value. */
    public static <T> Maybe<T> nullable(Supplier<? extends T> value) {
        return Maybe.lazy(() -> nullable(value.get()));
    }

    /** Eagerly convert an Optional to a Maybe. */
    public static <T> Maybe<T> from(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<? extends T> optional) {
        return optional.map(Maybe::<T>of).orElse(Maybe.empty());
    }

    /**
     * A view of a {@code Callable} as a {@code Maybe} that is empty if and only if {@code Callable.call()} throws an
     * exception or returns null.
     *
     * @see Either#from(Callable)
     * @see Sequence#from(Callable)
     */
    public static <T> Maybe<T> from(Callable<? extends T> value) {
        return Maybe.lazy(
            () -> {
                try {
                    return Maybe.nullable(value.call());
                } catch (Exception exception) {
                    return Maybe.empty();
                }
            }
        );
    }

    /**
     * A view of a {@code CompletableFuture} as a {@code Maybe} that is empty if and only if
     * {@code CompletableFuture.get()} throws an exception or returns null.
     *
     * @see Either#from(CompletableFuture)
     * @see Sequence#from(CompletableFuture)
     */
    public static <T> Maybe<T> from(CompletableFuture<? extends T> value) {
        return Maybe.lazy(
            () -> {
                try {
                    return Maybe.nullable(value.get());
                } catch (Exception exception) {
                    return Maybe.empty();
                }
            }
        );
    }

    /**
     * Wrap an eagerly evaluated non-null value if a condition is true.
     *
     * <p>This can be combined with {@link Maybe#lazy Maybe.lazy()} to defer evaluation of the condition:
     *
     * <pre>    {@code Maybe.lazy(() -> when(condition(), value)}</pre>
     *
     * @see Maybe#unless(boolean, Object)
     * @see Maybe#when(boolean, Supplier)
     */
    public static <T> Maybe<T> when(boolean condition, T value) {
        requireNonNull(value);
        return condition ? Maybe.of(value) : Maybe.empty();
    }

    /**
     * Wrap a lazily evaluated non-null value if a condition is true.
     *
     * <p>This can be combined with {@link Maybe#lazy Maybe.lazy()} to defer evaluation of the condition:
     *
     * <pre>    {@code Maybe.lazy(() -> when(condition(), value)}</pre>
     *
     * @see Maybe#unless(boolean, Supplier)
     * @see Maybe#when(boolean, Object)
     */
    public static <T> Maybe<T> when(boolean condition, Supplier<? extends T> value) {
        requireNonNull(value);
        return condition ? Maybe.of(value) : Maybe.empty();
    }

    /**
     * Wrap an eagerly evaluated non-null value if a condition is false.
     *
     * <p>This can be combined with {@link Maybe#lazy Maybe.lazy()} to defer evaluation of the condition:
     *
     * <pre>    {@code Maybe.lazy(() -> unless(condition(), value)}</pre>
     *
     * @see Maybe#when(boolean, Object)
     * @see Maybe#unless(boolean, Supplier)
     */
    public static <T> Maybe<T> unless(boolean condition, T value) {
        return Maybe.when(!condition, value);
    }

    /**
     * Wrap a lazily evaluated non-null value if a condition is false.
     *
     * <p>This can be combined with {@link Maybe#lazy Maybe.lazy()} to defer evaluation of the condition:
     *
     * <pre>    {@code Maybe.lazy(() -> unless(condition(), value)}</pre>
     *
     * @see Maybe#when(boolean, Supplier)
     * @see Maybe#unless(boolean, Object)
     */
    public static <T> Maybe<T> unless(boolean condition, Supplier<? extends T> value) {
        return Maybe.when(!condition, value);
    }

    /** Flatten a nested {@code Maybe}. */
    public static <T> Maybe<T> join(Maybe<? extends Maybe<? extends T>> maybe) {
        return Maybe.lazy(() -> maybe.matchLazy(Maybe::lazy, Maybe.empty()));
    }

    /** Safe covariant cast. */
    public static <T> Maybe<T> cast(Maybe<? extends T> maybe) {
        requireNonNull(maybe);
        @SuppressWarnings("unchecked") // safe because Maybe is immutable
        var widened = (Maybe<T>) maybe;
        return widened;
    }

    /** Adapt a function to work on values wrapped in {@code Maybe}. */
    public static <T, R> Function<Maybe<T>, Maybe<R>> lift(Function<? super T, ? extends R> function) {
        return maybe -> maybe.map(function);
    }

    /** Adapt a binary function to work on values wrapped in {@code Maybe}. */
    public static <T, U, R> BiFunction<Maybe<T>, Maybe<U>, Maybe<R>> lift(BiFunction<? super T, ? super U, ? extends R> function) {
        return (x, y) -> x.apply(y, function);
    }

    /**
     * Return a value defined in terms of the eagerly evaluated element contained in this Maybe if it exists, otherwise
     * return a lazy default value.
     *
     * <p>This method simulates pattern matching on this Maybe, forcing evaluation of the contained element. If the
     * element exists, it is passed to the given function and the result is returned. Otherwise, the result of invoking
     * the given supplier is returned.
     *
     * @param <R> the type of the result
     *
     * @see Maybe#match(Function, Object)
     * @see Maybe#matchLazy(Function, Supplier)
     * @see Maybe#or(Supplier)
     */
    public abstract <R> R match(Function<? super T, ? extends R> function, Supplier<? extends R> otherwise);

    /**
     * Return a value defined in terms of the eagerly evaluated element contained in this Maybe if it exists, otherwise
     * return an eager default value.
     *
     * <p>This method simulates pattern matching on this Maybe, forcing evaluation of the contained element. If the
     * element exists, it is passed to the given function and the result is returned. Otherwise, the given default
     * value is returned.
     *
     * @param <R> the type of the result
     *
     * @see Maybe#match(Function, Supplier)
     * @see Maybe#matchLazy(Function, Object)
     * @see Maybe#or(Object)
     */
    public abstract <R> R match(Function<? super T, ? extends R> function, R otherwise);

    /**
     * Return a value defined in terms of the lazily evaluated element contained in this Maybe if it exists, otherwise
     * return a lazy default value.
     *
     * <p>This method simulates pattern matching on this Maybe, deferring evaluation of the contained element. If the
     * element exists, it is passed to the given function, and the result is returned. Otherwise, the result of
     * invoking the given supplier is returned.
     *
     * <p>In contrast to {@link Maybe#match(Function,Supplier) Maybe.match()}, this method is lazy with respect to the
     * element of this Maybe. The caller of this method decides if and when to force evaluation of the element. This is
     * useful, for example, to preserve the laziness of an underlying Maybe in terms of which another value is
     * lazily defined.
     *
     * @param <R> the type of the result
     *
     * @see Maybe#matchLazy(Function, Object)
     * @see Maybe#match(Function, Supplier)
     * @see Maybe#or(Supplier)
     */
    public abstract <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, Supplier<? extends R> otherwise);


    /**
     * Return a value defined in terms of the lazily evaluated element contained in this Maybe if it exists, otherwise
     * return an eager default value.
     *
     * <p>This method simulates pattern matching on this Maybe, deferring evaluation of the contained element. A
     * supplier of the element is passed to the given function if it exists, and the result is returned. Otherwise, the
     * given default value is returned.
     *
     * <p>In contrast to {@link Maybe#match(Function,Object) Maybe.match()}, this method is lazy with respect to the
     * element of this Maybe. The caller of this method decides if and when to force evaluation of the element. This is
     * useful, for example, to preserve the laziness of an underlying Maybe in terms of which another value is
     * lazily defined.
     *
     * @param <R> the type of the result
     *
     * @see Maybe#matchLazy(Function, Supplier)
     * @see Maybe#match(Function, Object)
     * @see Maybe#or(Object)
     */
    public abstract <R> R matchLazy(Function<? super Supplier<T>, ? extends R> function, R otherwise);

    /**
     * Perform an action on the contained value if it exists.
     *
     * @see Maybe#forEachOrElse(Consumer, Runnable)
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        this.forEachOrElse(action, () -> {});
    }

    /**
     * If non-empty, perform an action on the contained value, otherwise perform a default action.
     *
     * @see Maybe#forEach(Consumer)
     */
    public void forEachOrElse(Consumer<? super T> action, Runnable otherwise) {
        requireNonNull(action);
        requireNonNull(otherwise);
        this.match(
            value -> {
                action.accept(value);
                return unit();
            },
            () -> {
                otherwise.run();
                return unit();
            }
        );
    }

    /**
     * A Maybe that computes its value at most once, the first time it is asked for, delegating to this Maybe and
     * caching the result.
     */
    public Maybe<T> memoize() {
        return this;
    }

    /**
     * Force the evaluation of and rewrap the contained value if it exists, otherwise return an empty {@code Maybe}.
     */
    public Maybe<T> eager() {
        return this;
    }

    /** Eagerly convert this to an {@code Optional}. */
    public Optional<T> optional() {
        return this.match(Optional::of, Optional.empty());
    }

    /** Lazily view this as a singleton sequence if non-empty, otherwise as an empty sequence. */
    public Sequence<T> sequence() {
        return Sequence.lazy(() -> this.matchLazy(Sequence::of, Sequence.empty()));
    }

    /** Lazily view this as a singleton stream if non-empty, otherwise as an empty stream. */
    public Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * An iterator over this {@code Maybe}.
     *
     * <p>The iterator throws {@link UnsupportedOperationException} on invocation of {@link Iterator#remove() remove()}.
     */
    @Override
    public Iterator<T> iterator() {
        return this.stream().iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return spliterator(this);
    }

    private static <T> Spliterator<T> spliterator(Maybe<T> maybe) {
        class MaybeSpliterator implements Spliterator<T> {
            private Maybe<T> maybe;

            MaybeSpliterator(Maybe<T> maybe) {
                this.maybe = maybe;
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                return this.maybe.match(
                    value -> {
                        action.accept(value);
                        this.maybe = Maybe.empty();
                        return true;
                    },
                    () -> {
                        this.maybe = Maybe.empty();
                        return false;
                    }
                );
            }

            @Override
            public void forEachRemaining(Consumer<? super T> action) {
                this.tryAdvance(action);
            }

            @Override
            public Spliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 1;
            }

            @Override
            public int characteristics() {
                return DISTINCT | NONNULL;
            }
        }

        return new MaybeSpliterator(maybe);
    }

    /**
     * True if and only if the argument is an instance of {@code Maybe} and either this and the argument are both empty
     * or both are non-empty and the contained values are equal.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof Maybe) {
            var that = (Maybe<?>) object;
            return this.matchLazy(
                value -> that.matchLazy(
                    Functions.apply(on(Objects::equals, Supplier::get), value),
                    false
                ),
                that::isEmpty
            );
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.match(value -> 31 + value.hashCode(), 1);
    }

    @Override
    public String toString() {
        return this.match(Functions.apply(String::format, "(%s)"), "()");
    }

    /** True if and only if no value is contained in this. */
    public boolean isEmpty() {
        return this.matchLazy(Functions.constant(false), true);
    }

    /** If non-empty, rewrap the contained value, otherwise return another {@code Maybe}. */
    public Maybe<T> or(Maybe<? extends T> otherwise) {
        requireNonNull(otherwise);
        return Maybe.lazy(() -> this.matchLazy(Maybe::of, otherwise));
    }

    /** If non-empty, return the contained value, otherwise return a default value. */
    public T or(T otherwise) {
        requireNonNull(otherwise);
        return this.match(Function.identity(), otherwise);
    }

    /** If non-empty, return the contained value, otherwise return a lazily evaluated default value. */
    public T or(Supplier<? extends T> otherwise) {
        requireNonNull(otherwise);
        return this.match(Function.identity(), otherwise);
    }

    /**
     * If non-empty, return the contained value, otherwise throw an {@code AssertionError}.
     *
     * @throws AssertionError if empty
     */
    public T orThrow() {
        return this.or(
            () -> {
                throw new AssertionError("value does not exist");
            }
        );
    }

    /**
     * If non-empty, return the contained value, otherwise throw a custom exception.
     *
     * @throws X if empty
     */
    public <X extends Throwable> T orThrow(Supplier<X> exception) throws X {
        return this.or(() -> sneak(exception));
    }

    @SuppressWarnings("unchecked")
    private static <T, X extends Throwable> T sneak(Supplier<? extends Throwable> exception) throws X {
        throw (X) exception.get();
    }

    /**
     * A lazy view of this as an {@code Either}, putting the contained value on the right if non-empty, otherwise
     * putting an {@code AssertionError} on the left.
     */
    public Either<AssertionError, T> right() {
        return this.rightOr(() -> new AssertionError("value does not exist"));
    }

    /**
     * A lazy view of this as an {@code Either}, putting the contained value on the right if non-empty, otherwise
     * putting an eagerly evaluated default value on the left.
     */
    public <A> Either<A, T> rightOr(A otherwise) {
        return Either.lazy(() -> this.matchLazy(Either::right, () -> Either.left(otherwise)));
    }

    /**
     * A lazy view of this as an {@code Either}, putting the contained value on the right if non-empty, otherwise
     * putting a lazily evaluated default value on the left.
     */
    public <A> Either<A, T> rightOr(Supplier<? extends A> otherwise) {
        return Either.lazy(() -> this.matchLazy(Either::right, () -> Either.left(otherwise)));
    }

    /**
     * A lazy view of this as an {@code Either}, putting the contained value on the left if non-empty, otherwise
     * putting the unit value on the right.
     */
    public Either<T, AssertionError> left() {
        return this.leftOr(() -> new AssertionError("value does not exist"));
    }

    /**
     * A lazy view of this as an {@code Either}, putting the contained value on the left if non-empty, otherwise
     * putting an eagerly evaluated default value on the right.
     */
    public <B> Either<T, B> leftOr(B otherwise) {
        return Either.lazy(() -> this.matchLazy(Either::left, () -> Either.right(otherwise)));
    }

    /**
     * A lazy view of this as an {@code Either}, putting the contained value on the left if non-empty, otherwise
     * putting a lazily evaluated default value on the right.
     */
    public <B> Either<T, B> leftOr(Supplier<? extends B> otherwise) {
        return Either.lazy(() -> this.matchLazy(Either::left, () -> Either.right(otherwise)));
    }

    /**
     * Apply a function to the contained value if it exists.
     *
     * @see Maybe#flatMap(Function)
     * @see Maybe#apply(Maybe)
     */
    public <R> Maybe<R> map(Function<? super T, ? extends R> function) {
        requireNonNull(function);
        return Maybe.lazy(
            () -> this.matchLazy(
                value -> Maybe.of(() -> function.apply(value.get())),
                Maybe.empty()
            )
        );
    }

    /**
     * Lift and lazily apply a function that returns a {@code Maybe}, flattening the result.
     *
     * @see Maybe#map(Function)
     * @see Maybe#apply(Maybe)
     * @see Maybe#join(Maybe)
     */
    public <R> Maybe<R> flatMap(Function<? super T, ? extends Maybe<? extends R>> function) {
        requireNonNull(function);
        return Maybe.lazy(
            () -> this.matchLazy(
                value -> Maybe.lazy(() -> function.apply(value.get())),
                Maybe.empty()
            )
        );
    }

    /**
     * Lazily apply a lifted function to this.
     *
     * <p>If either this or the argument are empty, the result is empty. Otherwise, wrap the result of applying the
     * function to the value.
     *
     * @see Maybe#apply(Maybe, BiFunction)
     * @see Maybe#map(Function)
     * @see Maybe#flatMap(Function)
     */
    public <R> Maybe<R> apply(Maybe<? extends Function<? super T, ? extends R>> function) {
        requireNonNull(function);
        return function.flatMap(this::map);
    }

    /** Lift and lazily apply a binary function to this and another {@code Maybe}. */
    public <U, R> Maybe<R> apply(Maybe<? extends U> maybe, BiFunction<? super T, ? super U, ? extends R> function) {
        requireNonNull(maybe);
        requireNonNull(function);
        return this.flatMap(value -> maybe.map(Functions.apply(function, value)));
    }

    /**
     * Lazily rewrap the contained value if it exists and satisfies a predicate, otherwise return an empty
     * {@code Maybe}.
     */
    public Maybe<T> filter(Predicate<? super T> predicate) {
        requireNonNull(predicate);
        return this.flatMap(value -> when(predicate.test(value), value));
    }

    /**
     * Narrow the type of the contained value if it exists.
     *
     * <p>If the value does not exist, or if it does exist but is not an instance of the given type, then the result is
     * empty.
     *
     * @see Maybe#filter(Predicate)
     * @see Maybe#cast(Maybe)
     * @see Sequence#narrow(Class)
     */
    public <R extends T> Maybe<R> narrow(Class<? extends R> type) {
        requireNonNull(type);
        return this.filter(type::isInstance).map(type::cast);
    }
}
