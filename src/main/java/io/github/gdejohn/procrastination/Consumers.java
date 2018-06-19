package io.github.gdejohn.procrastination;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Consumer combinators.
 *
 * <p>This class consists solely of static helper methods. It is not intended to be instantiated, so it has no public
 * constructors.
 *
 * @see Functions
 * @see Predicates
 */
public final class Consumers {
    private Consumers() {
        throw new AssertionError("this class is not intended to be instantiated");
    }

    /**
     * A Consumer that ignores its argument, returning immediately without doing anything.
     */
    public static Consumer<Object> noop() {
        return argument -> {};
    }

    /**
     * Safely contravariantly cast a consumer.
     *
     * @see Consumers#cast(BiConsumer)
     * @see Functions#cast(Function)
     * @see Predicates#cast(Predicate)
     */
    public static <T> Consumer<T> cast(Consumer<? super T> consumer) {
        @SuppressWarnings("unchecked") // safe because consumers are contravariant
        var narrowed = (Consumer<T>) consumer;
        return narrowed;
    }

    /**
     * Safely cast a binary consumer contravariantly with respect to each of its parameters.
     *
     * @see Consumers#cast(Consumer)
     * @see Functions#cast(BiFunction)
     * @see Predicates#cast(BiPredicate)
     */
    public static <T, U> BiConsumer<T, U> cast(BiConsumer<? super T, ? super U> action) {
        Objects.requireNonNull(action);
        @SuppressWarnings("unchecked") // safe because functions are immutable
        var widened = (BiConsumer<T, U>) action;
        return widened;
    }

    /**
     * Compute the fixed point of a higher-order function, yielding a recursive action.
     *
     * @see Functions#fix(UnaryOperator)
     * @see Predicates#fix(UnaryOperator)
     */
    public static <T> Consumer<T> fix(UnaryOperator<Consumer<T>> operator) {
        return operator.apply(variable -> fix(operator).accept(variable));
    }

    /**
     * A consumer that delegates to a binary consumer, duplicating its argument.
     *
     * <p>This method is equivalent to {@code f -> x -> f.accept(x, x)}.
     *
     * @see Consumers#join(Function)
     */
    public static <T> Consumer<T> join(BiConsumer<? super T, ? super T> action) {
        return argument -> action.accept(argument, argument);
    }

    /**
     * A consumer that delegates to a curried consumer, duplicating its argument.
     *
     * <p>This method is equivalent to {@code f -> x -> f.apply(x).accept(x)}.
     *
     * @see Consumers#join(BiConsumer)
     */
    public static <T> Consumer<T> join(Function<? super T, Consumer<? super T>> action) {
        return argument -> apply(action, argument, argument);
    }

    /**
     * Apply a curried consumer to two arguments.
     *
     * @see Consumers#apply(Function, Object, Object, Object)
     * @see Consumers#apply(Function, Object, Object, Object, Object)
     */
    public static <T, U> void apply(Function<? super T, ? extends Consumer<? super U>> action, T first, U second) {
        action.apply(first).accept(second);
    }

    /**
     * Apply a curried consumer to three arguments.
     *
     * @see Consumers#apply(Function, Object, Object)
     * @see Consumers#apply(Function, Object, Object, Object, Object)
     */
    public static <T, U, V> void apply(Function<? super T, ? extends Function<? super U, ? extends Consumer<? super V>>> action, T first, U second, V third) {
        Consumers.apply(action.apply(first), second, third);
    }

    /**
     * Apply a curried consumer to four arguments.
     *
     * @see Consumers#apply(Function, Object, Object)
     * @see Consumers#apply(Function, Object, Object, Object)
     */
    public static <T, U, V, W> void apply(Function<? super T, ? extends Function<? super U, ? extends Function<? super V, ? extends Consumer<? super W>>>> action, T first, U second, V third, W fourth) {
        Consumers.apply(action.apply(first), second, third, fourth);
    }

    /** Partially apply a binary consumer, fixing its first argument. */
    public static <T, U> Consumer<U> apply(BiConsumer<T, ? super U> consumer, T argument) {
        return x -> consumer.accept(argument, x);
    }

    /**
     * Flipped, curried consumer application.
     *
     * <p>Given an argument, this method returns a consumer that takes a consumer and applies it to the argument.
     * {@code x -> apply(x)} is equivalent to {@code x -> f -> f.apply(x)}, or {@code flip(curry(Consumer::accept))}.
     *
     * @see Consumers#flip(Function)
     * @see Consumers#curry(BiConsumer)
     * @see Consumer#accept(Object)
     * @see Functions#apply(Object)
     * @see Predicates#test(Object)
     */
    public static <T> Consumer<Consumer<T>> accept(T argument) {
        return action -> action.accept(argument);
    }

    /**
     * Compose an action with a function.
     *
     * <p>{@code compose(f,g)} is equivalent to {@code x -> f.accept(g.apply(x))}.
     *
     * @see Consumers#compose(BiConsumer, Function)
     * @see Consumers#on(BiConsumer, Function)
     * @see Functions#compose(Function, Function)
     * @see Predicates#compose(Predicate, Function)
     */
    public static <T, U> Consumer<T> compose(Consumer<? super U> action, Function<? super T, ? extends U> function) {
        return argument -> action.accept(function.apply(argument));
    }

    /**
     * A binary action that applies a function to its first argument and passes the result and its second argument to
     * another binary action.
     *
     * <p>{@code compose(f,g)} is equivalent to {@code (x,y) -> f.accept(g.apply(x),y)}.
     *
     * @see Consumers#compose(Consumer, Function)
     * @see Consumers#on(BiConsumer, Function)
     * @see Functions#compose(BiFunction, Function)
     * @see Predicates#compose(BiPredicate, Function)
     */
    public static <T, U, V> BiConsumer<T, V> compose(BiConsumer<? super U, ? super V> action, Function<? super T, ? extends U> function) {
        return (x, y) -> action.accept(function.apply(x), y);
    }

    /**
     * A binary action that applies a function to both of its arguments and passes the results to another binary action.
     *
     * <p>{@code on(f,g)} is equivalent to {@code (x,y) -> f.accept(g.apply(x),g.apply(y))}.
     *
     * @see Consumers#compose(Consumer, Function)
     * @see Consumers#compose(BiConsumer, Function)
     * @see Functions#on(BiFunction, Function)
     * @see Predicates#on(BiPredicate, Function)
     */
    public static <T, R> BiConsumer<T, T> on(BiConsumer<? super R, ? super R> action, Function<? super T, ? extends R> function) {
        return (x, y) -> action.accept(function.apply(x), function.apply(y));
    }

    /**
     * A binary action that applies one function to its first argument and another function to its second argument,
     * then passes the results to another binary action.
     *
     * @see Functions#on(BiFunction, Function)
     * @see Functions#zip(BiFunction, Function, Function)
     */
    public static <T, U, R, S> BiConsumer<T, U> zip(BiConsumer<? super R, ? super S> action, Function<? super T, ? extends R> first, Function<? super U, ? extends S> second) {
        return (x, y) -> action.accept(first.apply(x), second.apply(y));
    }

    /**
     * A binary action that applies one action to its first argument and another action to its second argument.
     *
     * @see Consumers#zip(BiConsumer, Function, Function)
     */
    public static <T, U> BiConsumer<T, U> zip(Consumer<? super T> first, Consumer<? super U> second) {
        return (x, y) -> {
            first.accept(x);
            second.accept(y);
        };
    }

    /** Reverse the order of the parameters of a binary consumer. */
    public static <T, U> BiConsumer<U, T> flip(BiConsumer<? super T, ? super U> consumer) {
        return (x, y) -> consumer.accept(y, x);
    }

    /** Reverse the order of the parameters of a curried consumer. */
    public static <T, U> Function<U, Consumer<T>> flip(Function<? super T, ? extends Consumer<? super U>> predicate) {
        return x -> y -> predicate.apply(y).accept(x);
    }

    /**
     * Combine the parameters of a binary consumer, yielding a consumer of pairs.
     *
     * @see Consumers#spread(Consumer)
     * @see Consumers#uncurry(Function)
     * @see Consumers#curry(BiConsumer)
     * @see Functions#gather(BiFunction)
     * @see Predicates#gather(BiPredicate)
     */
    public static <T, U> Consumer<Pair<T, U>> gather(BiConsumer<? super T, ? super U> action) {
        return pair -> pair.forBoth(action);
    }

    /**
     * Separate the parameter of a consumer of pairs, yielding a binary consumer.
     *
     * @see Consumers#gather(BiConsumer)
     * @see Consumers#curry(BiConsumer)
     * @see Consumers#uncurry(Function)
     * @see Functions#spread(Function)
     * @see Predicates#spread(Predicate)
     */
    public static <T, U> BiConsumer<T, U> spread(Consumer<? super Pair<T, U>> action) {
        return (x, y) -> action.accept(Pair.of(x, y));
    }

    /**
     * Adapt a binary consumer into a function returning a consumer.
     *
     * @see Consumers#uncurry(Function)
     * @see Consumers#spread(Consumer)
     * @see Consumers#gather(BiConsumer)
     * @see Functions#curry(BiFunction)
     * @see Predicates#curry(BiPredicate)
     */
    public static <T, U> Function<T, Consumer<U>> curry(BiConsumer<? super T, ? super U> action) {
        return x -> y -> action.accept(x, y);
    }

    /**
     * Adapt a function returning a consumer into a binary consumer.
     *
     * @see Consumers#curry(BiConsumer)
     * @see Consumers#gather(BiConsumer)
     * @see Consumers#spread(Consumer)
     * @see Functions#uncurry(Function)
     * @see Predicates#uncurry(Function)
     */
    public static <T, U> BiConsumer<T, U> uncurry(Function<? super T, ? extends Consumer<? super U>> action) {
        return (x, y) -> action.apply(x).accept(y);
    }

    /**
     * A consumer that accepts an exception and rethrows it wrapped it in a {@code RuntimeException}.
     *
     * @see Consumers#uncheck(Function)
     * @see RuntimeException#RuntimeException(Throwable) RuntimeException(Throwable)
     * @see Either#from(Callable)
     */
    public static Consumer<Exception> uncheck() {
        return Consumers.UNCHECK;
    }

    private static final Consumer<Exception> UNCHECK = uncheck(RuntimeException::new);

    /**
     * A consumer that accepts an exception {@code e} and throws an unchecked exception that is a function of {@code e}.
     *
     * @see Consumers#uncheck()
     * @see Either#from(Callable)
     */
    public static Consumer<Exception> uncheck(Function<? super Exception, ? extends RuntimeException> wrap) {
        Objects.requireNonNull(wrap);
        return cause -> {
            throw wrap.apply(cause);
        };
    }
}
