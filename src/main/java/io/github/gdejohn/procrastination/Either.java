package io.github.gdejohn.procrastination;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.gdejohn.procrastination.Functions.constant;
import static io.github.gdejohn.procrastination.Functions.on;
import static io.github.gdejohn.procrastination.Undefined.undefined;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.Objects.requireNonNull;

/**
 * A lazily evaluated, persistent collection with exactly one element that can take on one of two heterogeneous
 * possible values, labeled left and right.
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
 * @param <A> the type of the value if and only if it is on the left
 * @param <B> the type of the value if and only if it is on the right
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
    }

    private Either() {}

    /** Put an eager value on the right. */
    public static <A, B> Either<A, B> right(B value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return right.apply((Supplier<B>) () -> value);
            }

            @Override
            public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
                return right.apply(value);
            }

            @Override
            public Either<A, B> memoize() {
                return this;
            }
        };
    }

    /** Put a lazy value on the right. */
    public static <A, B> Either<A, B> right(Supplier<? extends B> value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return right.apply(Functions.map(Functions.memoize(value), Objects::requireNonNull));
            }

            @Override
            public Either<A, B> memoize() {
                Supplier<? extends B> memoized = Functions.memoize(value);
                return memoized == value ? this : Either.right(memoized);
            }
        };
    }

    /** Put an eager value on the left. */
    public static <A, B> Either<A, B> left(A value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return left.apply((Supplier<A>) () -> value);
            }

            @Override
            public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
                return left.apply(value);
            }

            @Override
            public Either<A, B> memoize() {
                return this;
            }
        };
    }

    /** Put a lazy value on the left. */
    public static <A, B> Either<A, B> left(Supplier<? extends A> value) {
        requireNonNull(value);
        return new Either<>() {
            @Override
            public <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right) {
                return left.apply(Functions.map(Functions.memoize(value), Objects::requireNonNull));
            }

            @Override
            public Either<A, B> memoize() {
                Supplier<? extends A> memoized = Functions.memoize(value);
                return memoized == value ? this : Either.left(memoized);
            }
        };
    }

    /**
     * A lazily evaluated {@code Either}.
     *
     * <p>This is useful for deferring the decision as to whether the value is on the left or the right.
     */
    public static <A, B> Either<A, B> lazy(Supplier<? extends Either<? extends A, ? extends B>> either) {
        return new Either.Proxy<>() {
            @Override
            protected Either<A, B> principal() {
                Either<A, B> principal = cast(either.get());
                while (principal instanceof Either.Proxy) {
                    principal = ((Either.Proxy<A, B>) principal).principal();
                }
                return principal.memoize();
            }
        };
    }

    /** A view of a {@code Callable} as an {@code Either}. */
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

    /** A view of a {@code CompletableFuture} as an {@code Either}. */
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
     * Lift and apply to an {@code Either} a function that accepts the value from either side, putting the result on
     * whichever side the value was on.
     *
     * @see Either#flatMap(Function, Either)
     * @see Either#apply(Either, Either)
     * @see Either#join(Either)
     * @see Either#mapEither(Function, Function)
     */
    public static <T, R> Either<R, R> map(Function<? super T, ? extends R> function, Either<? extends T, ? extends T> either) {
        requireNonNull(function);
        requireNonNull(either);
        return either.mapEither(function, function);
    }

    /**
     * Lift and apply to an {@code Either} a function that accepts the value from either side and returns an
     * {@code Either}, flattening the result.
     *
     * @see Either#map(Function, Either)
     * @see Either#apply(Either, Either)
     * @see Either#join(Either)
     * @see Either#flatMapEither(Function, Function)
     */
    public static <T, A, B> Either<A, B> flatMap(Function<? super T, ? extends Either<? extends A, ? extends B>> function, Either<? extends T, ? extends T> either) {
        requireNonNull(function);
        requireNonNull(either);
        return either.flatMapEither(function, function);
    }

    /**
     * Apply to an {@code Either} a lifted function that accepts the value from either side, putting the result on
     * whichever side the function was on.
     *
     * @see Either#map(Function, Either)
     * @see Either#flatMap(Function, Either)
     * @see Either#join(Either)
     * @see Either#applyRight(Either)
     * @see Either#applyLeft(Either)
     */
    public static <T, A, B> Either<A, B> apply(Either<? extends Function<? super T, ? extends A>, ? extends Function<? super T, ? extends B>> function, Either<? extends T, ? extends T> either) {
        requireNonNull(function);
        requireNonNull(either);
        return flatMap(value -> function.mapEither(Functions.apply(value), Functions.apply(value)), either);
    }

    /**
     * Lazily flatten a nested {@code Either}.
     *
     * @see Either#map(Function, Either)
     * @see Either#flatMap(Function, Either)
     * @see Either#apply(Either, Either)
     * @see Either#joinRight(Either)
     * @see Either#joinLeft(Either)
     */
    public static <A, B> Either<A, B> join(Either<? extends Either<? extends A, ? extends B>, ? extends Either<? extends A, ? extends B>> either) {
        requireNonNull(either);
        return Either.lazy(() -> either.matchLazy(Either::lazy, Either::lazy));
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
        return Either.lazy(() -> either.matchLazy(Either::left, Either::lazy));
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
        return Either.lazy(() -> either.matchLazy(Either::lazy, Either::right));
    }

    /**
     * Extract the value from an {@code Either}.
     *
     * @see Either#rightOr(Function)
     * @see Either#leftOr(Function)
     */
    public static <T> T merge(Either<? extends T, ? extends T> either) {
        return either.match(Function.identity(), Function.identity());
    }

    /**
     * An Either that computes its value at most once, the first time it is asked for, delegating to this Either and
     * caching the result.
     */
    public Either<A, B> memoize() {
        return Either.memoize(this);
    }

    private static <A, B> Either<A, B> memoize(Either<A, B> either) {
        Supplier<Either<A, B>> principal = Functions.memoize(
            () -> either.matchLazy(
                Functions.compose(Either::left, Functions::memoize),
                Functions.compose(Either::right, Functions::memoize)
            )
        );
        return new Either.Proxy<>() {
            @Override
            protected Either<A, B> principal() {
                return principal.get();
            }

            @Override
            public Either<A, B> memoize() {
                return this;
            }
        };
    }

    /** Force the evaluation of the contained value and rewrap it on the same side. */
    public Either<A, B> eager() {
        return this.match(Either::left, Either::right);
    }

    /**
     * Define a value in terms of either of the possible eagerly evaluated values.
     *
     * <p>This method simulates pattern matching on this {@code Either}, forcing evaluation of its element. It takes
     * two functions: one for each of the possible values the contained element can take on.
     */
    public <C> C match(Function<? super A, ? extends C> left, Function<? super B, ? extends C> right) {
        return this.matchLazy(left.compose(Supplier::get), right.compose(Supplier::get));
    }

    /**
     * Define a value in terms of either of the possible lazily evaluated values.
     *
     * <p>This method simulates pattern matching on this {@code Either}, deferring evaluation of its element. It takes
     * two functions: one for each of the possible values the contained element can take on.
     */
    public abstract <C> C matchLazy(Function<? super Supplier<A>, ? extends C> left, Function<? super Supplier<B>, ? extends C> right);

    /** Perform an action on the value if it is on the right. */
    public void forRight(Consumer<? super B> action) {
        this.matchLazy(
            constant(unit()),
            value -> {
                action.accept(value.get());
                return unit();
            }
        );
    }

    /** Perform an action on the value if it is on the left. */
    public void forLeft(Consumer<? super A> action) {
        this.matchLazy(
            value -> {
                action.accept(value.get());
                return unit();
            },
            constant(unit())
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

    /** Return the value if it is on the right, otherwise return a default value. */
    public B rightOr(B otherwise) {
        return this.matchLazy(constant(otherwise), Supplier::get);
    }

    /** Return the value if it is on the right, otherwise return a lazily evaluated default value. */
    public B rightOr(Supplier<? extends B> otherwise) {
        requireNonNull(otherwise);
        return this.matchLazy(value -> requireNonNull(otherwise.get()), Supplier::get);
    }

    /**
     * Return the value if it is on the right, otherwise apply a function to the value on the left.
     *
     * @see Either#right()
     * @see Either#rightOr(Object)
     * @see Either#rightOr(Supplier)
     * @see Either#rightOrThrow()
     * @see Either#rightOrThrow(Supplier)
     * @see Either#leftOr(Function)
     * @see Either#merge(Either)
     */
    public B rightOr(Function<? super A, ? extends B> otherwise) {
        return this.match(otherwise, Function.identity());
    }

    /**
     * Return the value if it is on the right, otherwise throw an {@code AssertionError}.
     *
     * @throws AssertionError if the value is not on the right
     */
    public B rightOrThrow() {
        return this.rightOr(
            () -> {
                throw new AssertionError("value is not on the right");
            }
        );
    }

    /**
     * Return the value if it is on the right, otherwise throw a custom exception.
     *
     * @throws X if the value is not on the right
     */
    public <X extends Throwable> B rightOrThrow(Supplier<X> exception) throws X {
        try {
            return this.rightOr(undefined());
        } catch (Undefined e) {
            throw exception.get();
        }
    }

    /** Return a {@code Maybe} containing the value if it is on the left, otherwise return an empty {@code Maybe}. */
    public Maybe<A> left() {
        return Maybe.lazy(() -> this.matchLazy(Maybe::of, constant(Maybe.empty())));
    }

    /** Return the value if it is on the left, otherwise return a default value. */
    public A leftOr(A otherwise) {
        requireNonNull(otherwise);
        return this.matchLazy(Supplier::get, constant(otherwise));
    }

    /** Return the value if it is on the left, otherwise return a lazily evaluated default value. */
    public A leftOr(Supplier<? extends A> otherwise) {
        requireNonNull(otherwise);
        return this.matchLazy(Supplier::get, value -> requireNonNull(otherwise.get()));
    }

    /**
     * Return the value if it is on the left, otherwise apply a function to the value on the right.
     *
     * @see Either#left()
     * @see Either#leftOr(Object)
     * @see Either#leftOr(Supplier)
     * @see Either#leftOrThrow()
     * @see Either#leftOrThrow(Supplier)
     * @see Either#rightOr(Function)
     * @see Either#merge(Either)
     */
    public A leftOr(Function<? super B, ? extends A> otherwise) {
        return this.match(Function.identity(), otherwise);
    }

    /**
     * Return the value if it is on the left, otherwise throw an {@code AssertionError}.
     *
     * @throws AssertionError if the value is not on the left
     */
    public A leftOrThrow() {
        return this.leftOr(
            () -> {
                throw new AssertionError("value is not on the left");
            }
        );
    }

    /**
     * Return the value if it is on the left, otherwise throw a custom exception.
     *
     * @throws X if the value is not on the left
     */
    public <X extends Throwable> A leftOrThrow(Supplier<X> exception) throws X {
        try {
            return this.leftOr(undefined());
        } catch (Undefined e) {
            throw exception.get();
        }
    }

    /** If the value is on the right, put it on the left; if the value is on the left, put it on the right. */
    public Either<B, A> swap() {
        return Either.lazy(() -> this.matchLazy(Either::right, Either::left));
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

    /** Lazily apply a lifted function to this, biased on the right. */
    public <C> Either<A, C> applyRight(Either<? extends A, ? extends Function<? super B, ? extends C>> function) {
        requireNonNull(function);
        return this.flatMapRight(value -> function.mapRight(Functions.apply(value)));
    }

    /** Lift a binary function and lazily apply it to this and another {@code Either}, biased on the right. */
    public <C, D> Either<A, D> applyRight(Either<? extends A, ? extends C> either, BiFunction<? super B, ? super C, ? extends D> function) {
        requireNonNull(either);
        requireNonNull(function);
        return this.flatMapRight(value -> either.mapRight(Functions.apply(function, value)));
    }

    /** Lazily apply a lifted function to this, biased on the left. */
    public <C> Either<C, B> applyLeft(Either<? extends Function<? super A, ? extends C>, ? extends B> function) {
        requireNonNull(function);
        return this.flatMapLeft(value -> function.mapLeft(Functions.apply(value)));
    }

    /** Lift a binary function and lazily apply it to this and another {@code Either}, biased on the left. */
    public <C, D> Either<D, B> applyLeft(Either<? extends C, ? extends B> either, BiFunction<? super A, ? super C, ? extends D> function) {
        requireNonNull(either);
        requireNonNull(function);
        return this.flatMapLeft(value -> either.mapLeft(Functions.apply(function, value)));
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
            var that = (Either<?, ?>) object;
            return this.matchLazy(
                left -> that.matchLazy(
                    Functions.apply(on(Objects::equals, Supplier::get), left),
                    constant(false)
                ),
                right -> that.matchLazy(
                    constant(false),
                    Functions.apply(on(Objects::equals, Supplier::get), right)
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
}
