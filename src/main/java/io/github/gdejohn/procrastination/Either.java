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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.gdejohn.procrastination.Functions.constant;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.Objects.requireNonNull;

/**
 * A lazily evaluated, persistent container with exactly one non-null element that can take on one of two possible
 * values, labeled {@link Either#left() left} and {@link Either#right() right}, which may have different types.
 *
 * <p>{@link Either} represents the sum of two types, whereas {@link Pair} represents the product of two types (and
 * {@link Unit} represents the product of zero types). {@link Maybe} represents the sum of a type and {@code Unit}.
 *
 * <p>{@code Either} is often used as an alternative to {@code Maybe} for modeling failure, conventionally failing on
 * the left and succeeding on the right (mnemonically, <i>right</i> is <i>correct</i>). In contrast to an empty
 * {@code Maybe}, the failure case with {@code Either} can carry information (e.g., an exception, or an error message).
 * In this context, {@code Either} serves a purpose similar to checked exceptions.
 *
 * <p>The {@code left} and {@code right} static factory methods can be used in conjunction with the conditional
 * operator (also known as the ternary operator):
 *
 * <pre>    {@code Function<Integer, Either<String, Integer>> fizz = n -> n % 3 == 0 ? left("Fizz") : right(n);}</pre>
 *
 * @param <A> the type of the element if it is on the left
 * @param <B> the type of the element if it is on the right
 */
public abstract class Either<A, B> {
    private static abstract class Proxy<A, B> extends Either<A, B> {
        protected Proxy() {}

        protected abstract Either<A, B> principal();

        @Override
        public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
            return this.principal().matchLazy(left, right);
        }

        @Override
        public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
            return this.principal().match(left, right);
        }

        @Override
        public Either<A, B> evaluate() {
            return this.principal().evaluate();
        }

        @Override
        public Either<B, A> swap() {
            return Either.lazy(() -> this.principal().swap());
        }
    }

    private Either() {}

    /**
     * A lazily evaluated {@code Either}.
     *
     * <p>This is useful for deferring the decision as to whether the value is on the left or the right.
     */
    public static <A, B> Either<A, B> lazy(Supplier<? extends Either<? extends A, ? extends B>> either) {
        requireNonNull(either);
        return new Either.Proxy<>() {
            @Override
            protected Either<A, B> principal() {
                return cast(either.get().memoize());
            }

            @Override
            public Either<A, B> memoize() {
                return Either.memoize(this);
            }
        };
    }

    private static <A, B> Either<A, B> memoize(Either.Proxy<A, B> either) {
        var principal = Functions.memoize(either::principal);
        return new Either.Proxy<>() {
            @Override
            protected Either<A, B> principal() {
                return principal.get();
            }
        };
    }

    /**
     * Put an eager value on the right.
     *
     * @see Either#left(Object)
     * @see Either#right(Supplier)
     * @see Either#lazy(Supplier)
     * @see Either#isRight()
     */
    public static <A, B> Either<A, B> right(B value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
                return right.apply(value);
            }

            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return right.apply((Supplier<B>) () -> value);
            }

            @Override
            public Maybe<B> right() {
                return Maybe.of(value);
            }

            @Override
            public Maybe<A> left() {
                return Maybe.empty();
            }

            @Override
            public <C> Either<A, C> mapRight(Function<? super B, ? extends C> function) {
                return Either.right(() -> function.apply(value));
            }

            @Override
            public <C> Either<C, B> mapLeft(Function<? super A, ? extends C> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the right
                var either = (Either<C, B>) this;
                return either;
            }

            @Override
            public <C, D> Either<C, D> mapEither(Function<? super A, ? extends C> left, Function<? super B, ? extends D> right) {
                return Either.right(() -> right.apply(value));
            }

            @Override
            public <C> Either<A, C> flatMapRight(Function<? super B, ? extends Either<? extends A, ? extends C>> function) {
                return Either.lazy(() -> function.apply(value));
            }

            @Override
            public <C> Either<C, B> flatMapLeft(Function<? super A, ? extends Either<? extends C, ? extends B>> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the right
                var either = (Either<C, B>) this;
                return either;
            }

            @Override
            public <C, D> Either<C, D> flatMapEither(Function<? super A, ? extends Either<? extends C, ? extends D>> left, Function<? super B, ? extends Either<? extends C, ? extends D>> right) {
                return Either.lazy(() -> right.apply(value));
            }
        };
    }

    /**
     * Put an eager value on the left.
     *
     * @see Either#right(Object)
     * @see Either#left(Supplier)
     * @see Either#lazy(Supplier)
     * @see Either#isLeft()
     */
    public static <A, B> Either<A, B> left(A value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
                return left.apply(value);
            }

            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return left.apply((Supplier<A>) () -> value);
            }

            @Override
            public Maybe<B> right() {
                return Maybe.empty();
            }

            @Override
            public Maybe<A> left() {
                return Maybe.of(value);
            }

            @Override
            public <C> Either<A, C> mapRight(Function<? super B, ? extends C> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the left
                var either = (Either<A, C>) this;
                return either;
            }

            @Override
            public <C> Either<C, B> mapLeft(Function<? super A, ? extends C> function) {
                return Either.left(() -> function.apply(value));
            }

            @Override
            public <C, D> Either<C, D> mapEither(Function<? super A, ? extends C> left, Function<? super B, ? extends D> right) {
                return Either.left(() -> left.apply(value));
            }

            @Override
            public <C> Either<A, C> flatMapRight(Function<? super B, ? extends Either<? extends A, ? extends C>> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the left
                var either = (Either<A, C>) this;
                return either;
            }

            @Override
            public <C> Either<C, B> flatMapLeft(Function<? super A, ? extends Either<? extends C, ? extends B>> function) {
                return Either.lazy(() -> function.apply(value));
            }

            @Override
            public <C, D> Either<C, D> flatMapEither(Function<? super A, ? extends Either<? extends C, ? extends D>> left, Function<? super B, ? extends Either<? extends C, ? extends D>> right) {
                return Either.lazy(() -> left.apply(value));
            }
        };
    }

    /**
     * Put a lazy value on the right.
     *
     * @see Either#left(Supplier)
     * @see Either#right(Object)
     * @see Either#lazy(Supplier)
     * @see Either#isRight()
     */
    public static <A, B> Either<A, B> right(Supplier<? extends B> value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
                return right.apply(requireNonNull(value.get()));
            }

            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return right.apply(Functions.memoize(value));
            }

            @Override
            public Either<A, B> memoize() {
                var memoized = Functions.memoize(value);
                return memoized == value ? this : Either.right(memoized);
            }

            @Override
            public Either<A, B> evaluate() {
                return Either.right(value.get());
            }

            @Override
            public Maybe<B> right() {
                return Maybe.of(value);
            }

            @Override
            public Maybe<A> left() {
                return Maybe.empty();
            }

            @Override
            public Either<B, A> swap() {
                return Either.left(value);
            }

            @Override
            public <C> Either<A, C> mapRight(Function<? super B, ? extends C> function) {
                return Either.right(() -> function.apply(value.get()));
            }

            @Override
            public <C> Either<C, B> mapLeft(Function<? super A, ? extends C> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the right
                var either = (Either<C, B>) this;
                return either;
            }

            @Override
            public <C, D> Either<C, D> mapEither(Function<? super A, ? extends C> left, Function<? super B, ? extends D> right) {
                return Either.right(() -> right.apply(value.get()));
            }

            @Override
            public <C> Either<A, C> flatMapRight(Function<? super B, ? extends Either<? extends A, ? extends C>> function) {
                return Either.lazy(() -> function.apply(value.get()));
            }

            @Override
            public <C> Either<C, B> flatMapLeft(Function<? super A, ? extends Either<? extends C, ? extends B>> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the right
                var either = (Either<C, B>) this;
                return either;
            }

            @Override
            public <C, D> Either<C, D> flatMapEither(Function<? super A, ? extends Either<? extends C, ? extends D>> left, Function<? super B, ? extends Either<? extends C, ? extends D>> right) {
                return Either.lazy(() -> right.apply(value.get()));
            }
        };
    }

    /**
     * Put a lazy value on the left.
     *
     * @see Either#right(Supplier)
     * @see Either#left(Object)
     * @see Either#lazy(Supplier)
     * @see Either#isLeft()
     */
    public static <A, B> Either<A, B> left(Supplier<? extends A> value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
                return left.apply(requireNonNull(value.get()));
            }

            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return left.apply(Functions.memoize(value));
            }

            @Override
            public Either<A, B> memoize() {
                var memoized = Functions.memoize(value);
                return memoized == value ? this : Either.left(memoized);
            }

            @Override
            public Either<A, B> evaluate() {
                return Either.left(value.get());
            }

            @Override
            public Maybe<B> right() {
                return Maybe.empty();
            }

            @Override
            public Maybe<A> left() {
                return Maybe.of(value);
            }

            @Override
            public Either<B, A> swap() {
                return Either.right(value);
            }

            @Override
            public <C> Either<A, C> mapRight(Function<? super B, ? extends C> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the left
                var either = (Either<A, C>) this;
                return either;
            }

            @Override
            public <C> Either<C, B> mapLeft(Function<? super A, ? extends C> function) {
                return Either.left(() -> function.apply(value.get()));
            }

            @Override
            public <C, D> Either<C, D> mapEither(Function<? super A, ? extends C> left, Function<? super B, ? extends D> right) {
                return Either.left(() -> left.apply(value.get()));
            }

            @Override
            public <C> Either<A, C> flatMapRight(Function<? super B, ? extends Either<? extends A, ? extends C>> function) {
                @SuppressWarnings("unchecked") // safe because the value is already on the left
                var either = (Either<A, C>) this;
                return either;
            }

            @Override
            public <C> Either<C, B> flatMapLeft(Function<? super A, ? extends Either<? extends C, ? extends B>> function) {
                return Either.lazy(() -> function.apply(value.get()));
            }

            @Override
            public <C, D> Either<C, D> flatMapEither(Function<? super A, ? extends Either<? extends C, ? extends D>> left, Function<? super B, ? extends Either<? extends C, ? extends D>> right) {
                return Either.lazy(() -> left.apply(value.get()));
            }
        };
    }

    /** View a {@code Callable} as an {@code Either}. */
    public static <T> Either<Exception, T> from(Callable<? extends T> value) {
        return Either.lazy(
            () -> {
                try {
                    return Either.right(requireNonNull(value.call()));
                } catch (Exception exception) {
                    return Either.left(exception);
                }
            }
        );
    }

    /** View a {@code CompletableFuture} as an {@code Either}. */
    public static <T> Either<Exception, T> from(CompletableFuture<? extends T> value) {
        return Either.lazy(
            () -> {
                try {
                    return Either.right(requireNonNull(value.get()));
                } catch (Exception exception) {
                    return Either.left(exception);
                }
            }
        );
    }

    /** Safe covariant cast. */
    public static <A, B> Either<A, B> cast(Either<? extends A, ? extends B> either) {
        requireNonNull(either);
        @SuppressWarnings("unchecked") // safe because Either is immutable
        var widened = (Either<A, B>) either;
        return widened;
    }

    /**
     * Lazily flatten a right-nested {@code Either}.
     *
     * @see Either#flatMapRight(Function)
     * @see Either#joinLeft(Either)
     * @see Either#join(Either)
     */
    public static <A, B> Either<A, B> joinRight(Either<? extends A, ? extends Either<? extends A, ? extends B>> either) {
        requireNonNull(either);
        return Either.lazy(() -> either.matchLazy(Either::left, Supplier::get));
    }

    /**
     * Lazily flatten a left-nested {@code Either}.
     *
     * @see Either#flatMapLeft(Function)
     * @see Either#joinRight(Either)
     * @see Either#join(Either)
     */
    public static <A, B> Either<A, B> joinLeft(Either<? extends Either<? extends A, ? extends B>, ? extends B> either) {
        requireNonNull(either);
        return Either.lazy(() -> either.matchLazy(Supplier::get, Either::right));
    }

    /**
     * Lazily flatten a nested {@code Either}.
     *
     * @see Either#joinRight(Either)
     * @see Either#joinLeft(Either)
     */
    public static <A, B> Either<A, B> join(Either<? extends Either<? extends A, ? extends B>, ? extends Either<? extends A, ? extends B>> either) {
        requireNonNull(either);
        return Either.lazy(() -> either.matchLazy(Supplier::get, Supplier::get));
    }

    /**
     * Extract the value from an {@code Either}.
     *
     * <p>This is convenient in situations where it's not important whether the contained value was on the left or the
     * right. Testing the type of the returned value to figure out which side it was on defeats the purpose of this
     * class.
     */
    public static <T> T merge(Either<? extends T, ? extends T> either) {
        return either.match(Function.identity(), Function.identity());
    }

    /**
     * Return a value defined in terms of either of the possible eagerly evaluated elements contained in this Either.
     *
     * <p>This method simulates pattern matching on this Either, forcing evaluation of its element. The element is
     * passed to the first function if it's on the left, or to the second function if it's on the right, and the result
     * of whichever function was called is returned.
     */
    public abstract <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right);

    /**
     * Return a value defined in terms of either of the possible lazily evaluated elements contained in this Either.
     *
     * <p>This method simulates pattern matching on this Either, deferring evaluation of its element. A supplier of the
     * element is passed to the first function if it's on the left, or to the second function if it's on the right, and
     * the result of whichever function was called is returned.
     *
     * <p>In contrast to {@link Either#match(Function,Function) Either.match()}, this method is lazy with respect to
     * the element of this Either. The caller of this method decides if and when to force evaluation of the element.
     * This is useful, for example, to preserve the laziness of an underlying Either in terms of which another value is
     * lazily defined.
     */
    public abstract <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right);

    /** Perform an action on the value if it is on the right. */
    public Either<A, B> forRight(Consumer<? super B> action) {
        return this.matchLazy(
            constant(this),
            value -> {
                action.accept(value.get());
                return this;
            }
        );
    }

    /** Perform an action on the value if it is on the left. */
    public Either<A, B> forLeft(Consumer<? super A> action) {
        return this.matchLazy(
            value -> {
                action.accept(value.get());
                return this;
            },
            constant(this)
        );
    }

    /** Perform one of two actions on the value depending on whether it is on the left or the right. */
    public void forEither(Consumer<? super A> left, Consumer<? super B> right) {
        this.match(
            value -> {
                left.accept(value);
                return unit();
            },
            value -> {
                right.accept(value);
                return unit();
            }
        );
    }

    /**
     * An Either that computes its value at most once, the first time it is asked for, delegating to this Either and
     * caching the result.
     */
    public Either<A, B> memoize() {
        return this;
    }

    /** Force the evaluation of the contained value and rewrap it on the same side. */
    public Either<A, B> evaluate() {
        return this;
    }

    /**
     * True if and only if the argument is an instance of {@code Either} and its value is on the same side as the value
     * contained in this and the values are equal.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof Either) {
            return ((Either<?, ?>) object).matchLazy(
                left -> this.matchLazy(
                    value -> value.get().equals(left.get()),
                    constant(false)
                ),
                right -> this.matchLazy(
                    constant(false),
                    value -> value.get().equals(right.get())
                )
            );
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.match(
            value -> (31 + value.hashCode()) * 31 + 1,
            value -> (31 + 1) * 31 + value.hashCode()
        );
    }

    @Override
    public String toString() {
        return this.match(
            Functions.apply(String::format, "(%s, ())"),
            Functions.apply(String::format, "((), %s)")
        );
    }

    /** True if and only if the value is on the right. */
    public boolean isRight() {
        return this.matchLazy(constant(false), constant(true));
    }

    /** True if and only if the value is on the left. */
    public boolean isLeft() {
        return this.matchLazy(constant(true), constant(false));
    }

    /** Return a {@code Maybe} containing the value if it is on the right, otherwise return an empty {@code Maybe}. */
    public Maybe<B> right() {
        return Maybe.lazy(() -> this.matchLazy(constant(Maybe.empty()), Maybe::of));
    }

    /** Return a {@code Maybe} containing the value if it is on the left, otherwise return an empty {@code Maybe}. */
    public Maybe<A> left() {
        return Maybe.lazy(() -> this.matchLazy(Maybe::of, constant(Maybe.empty())));
    }

    /** If the value is on the right, put it on the left; if the value is on the left, put it on the right. */
    public Either<B, A> swap() {
        return this.match(Either::right, Either::left);
    }

    /** Apply a function to the value if it is on the right. */
    public <C> Either<A, C> mapRight(Function<? super B, ? extends C> function) {
        return Either.lazy(
            () -> this.matchLazy(
                Either::left,
                value -> Either.right(() -> function.apply(value.get()))
            )
        );
    }

    /** Apply a function to the value if it is on the left. */
    public <C> Either<C, B> mapLeft(Function<? super A, ? extends C> function) {
        return Either.lazy(
            () -> this.matchLazy(
                value -> Either.left(() -> function.apply(value.get())),
                Either::right
            )
        );
    }

    /** Apply one of two functions to the value depending on whether it is on the left or the right. */
    public <C, D> Either<C, D> mapEither(Function<? super A, ? extends C> left, Function<? super B, ? extends D> right) {
        return Either.lazy(
            () -> this.matchLazy(
                value -> Either.left(() -> left.apply(value.get())),
                value -> Either.right(() -> right.apply(value.get()))
            )
        );
    }

    /**
     * Apply an {@code Either}-yielding function to the value if it is on the right and flatten the nested result.
     *
     * @see Either#joinRight(Either)
     */
    public <C> Either<A, C> flatMapRight(Function<? super B, ? extends Either<? extends A, ? extends C>> function) {
        return Either.lazy(
            () -> this.matchLazy(
                Either::left,
                value -> Either.lazy(() -> function.apply(value.get()))
            )
        );
    }

    /**
     * Apply an {@code Either}-yielding function to the value if it is on the left and flatten the nested result.
     *
     * @see Either#joinLeft(Either)
     */
    public <C> Either<C, B> flatMapLeft(Function<? super A, ? extends Either<? extends C, ? extends B>> function) {
        return Either.lazy(
            () -> this.matchLazy(
                value -> Either.lazy(() -> function.apply(value.get())),
                Either::right
            )
        );
    }

    /**
     * Apply one of two {@code Either}-yielding functions to the value depending on whether it is on the left or the
     * right and flatten the nested result.
     *
     * @see Either#join(Either)
     */
    public <C, D> Either<C, D> flatMapEither(Function<? super A, ? extends Either<? extends C, ? extends D>> left, Function<? super B, ? extends Either<? extends C, ? extends D>> right) {
        return Either.lazy(
            () -> this.matchLazy(
                value -> Either.lazy(() -> left.apply(value.get())),
                value -> Either.lazy(() -> right.apply(value.get()))
            )
        );
    }

    /**
     * Lazily apply a lifted function to this {@code Either}, biased on the right.
     *
     * <p>If the value of the given {@code Either} is on the left, return that value on the left. Otherwise, if the
     * value of this {@code Either} is on the left, return that value on the left. Otherwise, apply the function
     * on the right of the given {@code Either} to the value on the right of this {@code Either} and return the result
     * on the right.
     */
    public <C> Either<A, C> applyRight(Either<? extends A, ? extends Function<? super B, ? extends C>> function) {
        return Either.<A, Function<? super B, ? extends C>>cast(function).flatMapRight(this::mapRight);
    }

    /** Lift a binary function and lazily apply it to this and another {@code Either}, biased on the right. */
    public <C, D> Either<A, D> applyRight(Either<? extends A, ? extends C> either, BiFunction<? super B, ? super C, ? extends D> function) {
        requireNonNull(either);
        requireNonNull(function);
        return this.flatMapRight(value -> either.mapRight(Functions.apply(function, value)));
    }

    /**
     * Lazily apply a lifted function to this {@code Either}, biased on the left.
     *
     * <p>If the value of the given {@code Either} is on the right, return that value on the right. Otherwise, if the
     * value of this {@code Either} is on the right, return that value on the right. Otherwise, apply the function
     * on the left of the given {@code Either} to the value on the left of this {@code Either} and return the result
     * on the left.
     */
    public <C> Either<C, B> applyLeft(Either<? extends Function<? super A, ? extends C>, ? extends B> function) {
        return Either.<Function<? super A, ? extends C>, B>cast(function).flatMapLeft(this::mapLeft);
    }

    /** Lift a binary function and lazily apply it to this and another {@code Either}, biased on the left. */
    public <C, D> Either<D, B> applyLeft(Either<? extends C, ? extends B> either, BiFunction<? super A, ? super C, ? extends D> function) {
        requireNonNull(either);
        requireNonNull(function);
        return this.flatMapLeft(value -> either.mapLeft(Functions.apply(function, value)));
    }
}
