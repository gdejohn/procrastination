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

import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Function combinators.
 *
 * <p>This class consists solely of static helper methods. It is not intended to be instantiated, so it has no public
 * constructors.
 *
 * @see Predicates
 * @see Consumers
 */
public final class Functions {
    private Functions() {
        throw new AssertionError("this class is not intended to be instantiated");
    }

    /**
     * A thread-safe Supplier that computes its value at most once, delegating to the given Supplier, and caches the
     * result.
     */
    public static <T> Supplier<T> memoize(Supplier<? extends T> supplier) {
        requireNonNull(supplier);

        class Proxy implements Supplier<T> {
            private boolean initialized = false;

            private Supplier<T> delegate;

            Proxy(Supplier<? extends T> principal) {
                requireNonNull(principal);
                this.delegate = () -> {
                    synchronized(this) {
                        if(!this.initialized) {
                            T result = principal.get();
                            this.delegate = () -> result;
                            this.initialized = true;
                            return result;
                        } else {
                            return this.delegate.get();
                        }
                    }
                };
            }

            @Override
            public T get() {
                return this.delegate.get();
            }
        }

        return supplier instanceof Proxy ? (Proxy) supplier : new Proxy(supplier);
    }

    /**
     * Safely cast a supplier covariantly with respect to its result type.
     *
     * @see Functions#cast(Function)
     * @see Functions#cast(BiFunction)
     */
    public static <T> Supplier<T> cast(Supplier<? extends T> supplier) {
        requireNonNull(supplier);
        @SuppressWarnings("unchecked") // safe because suppliers are immutable
        var widened = (Supplier<T>) supplier;
        return widened;
    }

    /**
     * Safely cast a function contravariantly with respect to its parameter and covariantly with respect to its return
     * type.
     *
     * @see Functions#cast(BiFunction)
     * @see Functions#cast(Supplier)
     * @see Predicates#cast(Predicate)
     * @see Consumers#cast(Consumer)
     */
    public static <T, R> Function<T, R> cast(Function<? super T, ? extends R> function) {
        requireNonNull(function);
        @SuppressWarnings("unchecked") // safe because functions are immutable
        var f = (Function<T, R>) function;
        return f;
    }

    /**
     * Safely cast a binary function contravariantly with respect to each of its parameters and covariantly with
     * respect to its return type.
     *
     * @see Functions#cast(Function)
     * @see Functions#cast(Supplier)
     * @see Predicates#cast(BiPredicate)
     * @see Consumers#cast(BiConsumer)
     */
    public static <T, U, R> BiFunction<T, U, R> cast(BiFunction<? super T, ? super U, ? extends R> function) {
        requireNonNull(function);
        @SuppressWarnings("unchecked") // safe because functions are immutable
        var f = (BiFunction<T, U, R>) function;
        return f;
    }

    /** A supplier that delegates to another supplier, projecting the result through a function. */
    public static <T, R> Supplier<R> map(Supplier<T> supplier, Function<? super T, ? extends R> function) {
        requireNonNull(supplier);
        requireNonNull(function);
        return () -> function.apply(supplier.get());
    }

    /**
     * Apply a function to an argument.
     *
     * <p>This is useful as a kind of let-expression, where the argument is an expression that does not denote a
     * variable (e.g., a method invocation, a compound expression) and the function is a lambda whose body refers to
     * its argument more than once.
     *
     * @see Functions#let(Object, Object, BiFunction)
     * @see Functions#apply(BiFunction, Object)
     * @see Predicates#let(Object, Predicate)
     */
    public static <T, R> R let(T argument, Function<T, ? extends R> function) {
        return function.apply(argument);
    }

    /**
     * Apply a binary function to two arguments.
     *
     * <p>This is useful as a kind of let-expression, where the arguments are expressions that do not denote variables
     * (e.g., method invocations, compound expressions) and the function is a lambda whose body refers to each of its
     * arguments more than once.
     *
     * @see Functions#let(Object, Function)
     * @see Functions#apply(BiFunction, Object)
     * @see Predicates#let(Object, Object, BiPredicate)
     */
    public static <T, U, R> R let(T first, U second, BiFunction<T, U, ? extends R> function) {
        return function.apply(first, second);
    }

    /**
     * Apply a curried function to two arguments.
     *
     * @see Functions#apply(Function, Object, Object, Object)
     * @see Functions#apply(Function, Object, Object, Object, Object)
     */
    public static <T, U, R> R apply(Function<? super T, ? extends Function<? super U, ? extends R>> function, T first, U second) {
        return function.apply(first).apply(second);
    }

    /**
     * Apply a curried function to three arguments.
     *
     * @see Functions#apply(Function, Object, Object)
     * @see Functions#apply(Function, Object, Object, Object, Object)
     */
    public static <T, U, V, R> R apply(Function<? super T, ? extends Function<? super U, ? extends Function<? super V, ? extends R>>> function, T first, U second, V third) {
        return Functions.apply(function.apply(first), second, third);
    }

    /**
     * Apply a curried function to four arguments.
     *
     * @see Functions#apply(Function, Object, Object)
     * @see Functions#apply(Function, Object, Object, Object)
     */
    public static <T, U, V, W, R> R apply(Function<? super T, ? extends Function<? super U, ? extends Function<? super V, ? extends Function<? super W, ? extends R>>>> function, T first, U second, V third, W fourth) {
        return Functions.apply(function.apply(first), second, third, fourth);
    }

    /** Partially apply a binary function, fixing its first argument. */
    public static <T, U, R> Function<U, R> apply(BiFunction<? super T, ? super U, ? extends R> function, T argument) {
        requireNonNull(function);
        return x -> function.apply(argument, x);
    }

    /**
     * Flipped, curried function application.
     *
     * <p>Given an argument, this method returns a function that takes a function and applies it to the argument.
     * {@code x -> apply(x)} is equivalent to {@code x -> f -> f.apply(x)}, or {@code flip(curry(Function::apply))}.
     *
     * @see Functions#flip(Function)
     * @see Functions#curry(BiFunction)
     * @see Function#apply(Object)
     * @see Predicates#test(Object)
     * @see Consumers#accept(Object)
     */
    public static <T, R> Function<Function<? super T, ? extends R>, R> apply(T argument) {
        return function -> function.apply(argument);
    }

    /** A function that always ignores its argument and returns the same non-null value. */
    public static <R> Function<Object, R> constant(R value) {
        requireNonNull(value);
        return argument -> value;
    }

    /**
     * A function that delegates to a binary function, duplicating its argument.
     *
     * <p>{@code f -> join(f)} is equivalent to {@code f -> x -> f.apply(x, x)}.
     *
     * @see Functions#join(Function)
     */
    public static <T, R> Function<T, R> join(BiFunction<? super T, ? super T, ? extends R> function) {
        return argument -> function.apply(argument, argument);
    }

    /**
     * A function that delegates to a curried function, duplicating its argument.
     *
     * <p>{@code f -> join(f)} is equivalent to {@code f -> x -> f.apply(x).apply(x)}.
     *
     * @see Functions#join(BiFunction)
     */
    public static <T, R> Function<T, R> join(Function<? super T, ? extends  Function<? super T, ? extends R>> function) {
        return argument -> apply(function, argument, argument);
    }

    /**
     * Compute the fixed point of a higher-order function, enabling recursive lambda expressions (i.e., anonymous
     * recursion).
     *
     * <p>Lambda expressions by definition are unnamed, making explicit recursion impossible. The trick here is to
     * abstract the recursive call by accepting the function itself as another argument and letting {@code fix()} tie
     * the knot. For example:
     *
     * <pre>    {@code Function<Integer, Integer> factorial = fix(f -> n -> n == 0 ? 1 : n * f.apply(n - 1));}</pre>
     *
     * <p>If more than one parameter is needed, just keep currying ({@link Functions#apply(Function, Object, Object)
     * Functions.apply} simplifies passing arguments to curried functions). For example:
     *
     * <pre>    {@code BiFunction<Integer, Integer, Integer> ackermann = uncurry(
     *        fix(
     *            f -> m -> n -> {
     *                if (m == 0) {
     *                    return n + 1;
     *                } else if (m > 0 && n == 0) {
     *                    return apply(f, m - 1, 1);
     *                } else {
     *                    return apply(f, m - 1, apply(f, m, n - 1));
     *                }
     *            }
     *        )
     *    );}</pre>
     *
     * <p>Deep recursion can cause a stack overflow.
     * See {@link Trampoline#evaluate(Object, UnaryOperator) Trampoline.evaluate} for a stack-safe alternative.
     *
     * @see Predicates#fix(UnaryOperator)
     * @see Consumers#fix(UnaryOperator)
     */
    public static <T, R> Function<T, R> fix(UnaryOperator<Function<T, R>> function) {
        return function.apply(argument -> fix(function).apply(argument));
    }

    /**
     * Combine the parameters of a binary function, yielding a function of pairs.
     *
     * @see Functions#spread(Function)
     * @see Functions#uncurry(Function)
     * @see Functions#curry(BiFunction)
     * @see Predicates#gather(BiPredicate)
     * @see Consumers#gather(BiConsumer)
     */
    public static <T, U, R> Function<Pair<T, U>, R> gather(BiFunction<? super T, ? super U, ? extends R> function) {
        requireNonNull(function);
        return pair -> pair.match(function);
    }

    /**
     * Separate the parameter of a function of pairs, yielding a binary function.
     *
     * @see Functions#gather(BiFunction)
     * @see Functions#curry(BiFunction)
     * @see Functions#uncurry(Function)
     * @see Predicates#spread(Predicate)
     * @see Consumers#spread(Consumer)
     */
    public static <T, U, R> BiFunction<T, U, R> spread(Function<? super Pair<T, U>, ? extends R> function) {
        requireNonNull(function);
        return (x, y) -> function.apply(Pair.of(x, y));
    }

    /**
     * Adapt a binary function into a function returning a function.
     *
     * @see Functions#uncurry(Function)
     * @see Functions#spread(Function)
     * @see Functions#gather(BiFunction)
     * @see Predicates#curry(BiPredicate)
     * @see Consumers#curry(BiConsumer)
     */
    public static <T, U, R> Function<T, Function<U, R>> curry(BiFunction<? super T, ? super U, ? extends R> function) {
        requireNonNull(function);
        return x -> y -> function.apply(x, y);
    }

    /**
     * Adapt a function returning a function into a binary function.
     *
     * @see Functions#curry(BiFunction)
     * @see Functions#gather(BiFunction)
     * @see Functions#spread(Function)
     * @see Predicates#uncurry(Function)
     * @see Consumers#uncurry(Function)
     */
    public static <T, U, R> BiFunction<T, U, R> uncurry(Function<? super T, ? extends Function<? super U, ? extends R>> function) {
        requireNonNull(function);
        return (x, y) ->  apply(function, x, y);
    }

    /**
     * A function that applies a function to its argument and passes the result to another function.
     *
     * {@code compose(f,g)} is equivalent to {@code x -> f.apply(g.apply(x))}.
     *
     * <p>{@code compose(f,g)} is also equivalent to {@link Function#compose f.compose(g)} and
     * {@link Function#andThen g.andThen(f)}, but is more convenient if {@code f} and {@code g} are method references.
     *
     * @see Functions#compose(BiFunction, Function)
     * @see Functions#on(BiFunction, Function)
     * @see Predicates#compose(Predicate, Function)
     * @see Consumers#compose(Consumer, Function)
     */
    public static <T, U, R> Function<T, R> compose(Function<? super U, ? extends R> f, Function<? super T, ? extends U> g) {
        requireNonNull(f);
        requireNonNull(g);
        return x -> f.apply(g.apply(x));
    }

    /**
     * A binary function that applies a function to its first argument and passes the result and its second argument to
     * another binary function.
     *
     * <p>{@code compose(f,g)} is equivalent to {@code (x,y) -> f.apply(g.apply(x),y)}.
     *
     * @see Functions#compose(Function, Function)
     * @see Functions#on(BiFunction, Function)
     * @see Predicates#compose(BiPredicate, Function)
     * @see Consumers#compose(BiConsumer, Function)
     */
    public static <T, S, U, R> BiFunction<T, U, R> compose(BiFunction<? super S, ? super U, ? extends R> f, Function<? super T, ? extends S> g) {
        requireNonNull(f);
        requireNonNull(g);
        return (x, y) -> f.apply(g.apply(x), y);
    }

    /**
     * {@code andThen(f,g)} is equivalent to {@code (x,y) -> g.apply(f.apply(x,y))}.
     *
     * <p>{@code andThen(f,g)} is also equivalent to {@link BiFunction#andThen f.andThen(g)}, but more convenient if
     * {@code f} is a method reference.
     *
     * @see Functions#on(BiFunction, Function)
     * @see Functions#compose(Function, Function)
     * @see Predicates#compose(BiPredicate, Function)
     */
    public static <T, U, S, R> BiFunction<T, U, R> andThen(BiFunction<? super T, ? super U, ? extends S> f, Function<? super S, ? extends R> g) {
        requireNonNull(f);
        requireNonNull(g);
        return (x, y) -> g.apply(f.apply(x, y));
    }

    /**
     * A binary function that applies a function to both of its arguments and passes the results to another binary
     * function.
     *
     * <p>{@code on(f,g)} is equivalent to {@code (x,y) -> f.apply(g.apply(x),g.apply(y))}.
     *
     * @see Functions#compose(Function, Function)
     * @see Functions#compose(BiFunction, Function)
     * @see Predicates#on(BiPredicate, Function)
     * @see Consumers#on(BiConsumer, Function)
     */
    public static <T, U, R> BiFunction<T, T, R> on(BiFunction<? super U, ? super U, ? extends R> f, Function<? super T, U> g) {
        requireNonNull(f);
        requireNonNull(g);
        return (x, y) -> f.apply(g.apply(x), g.apply(y));
    }

    /**
     * A binary function that applies one function to its first argument and another function to its second argument,
     * then passes the results to another binary function.
     *
     * @see Consumers#zip(BiConsumer, Function, Function)
     */
    public static <T, U, V, W, R> BiFunction<T, U, R> zip(BiFunction<? super V, ? super W, ? extends R> combine, Function<? super T, ? extends V> first, Function<? super U, ? extends W> second) {
        return (x, y) -> combine.apply(first.apply(x), second.apply(y));
    }

    /** Swap the parameters of a binary function. */
    public static <T, U, R> BiFunction<T, U, R> flip(BiFunction<? super U, ? super T, ? extends R> function) {
        requireNonNull(function);
        return (x, y) -> function.apply(y, x);
    }

    /** Swap the first two parameters of a curried function. */
    public static <T, U, R> Function<T, Function<U, R>> flip(Function<? super U, ? extends Function<? super T, ? extends R>> function) {
        requireNonNull(function);
        return x -> y -> function.apply(y).apply(x);
    }

    /**
     * A binary operator that returns the greater of its two arguments, according to their natural order.
     *
     * <p>If equal, the first argument is returned.
     *
     * @see Functions#minimum()
     * @see Functions#minimum(Comparator)
     * @see Functions#maximum(Comparator)
     */
    public static <T extends Comparable<? super T>> BinaryOperator<T> maximum() {
        return (x, y) -> x.compareTo(y) >= 0 ? x : y;
    }

    /**
     * A binary operator that returns the greater of its two arguments, according to a comparator.
     *
     * <p>If equal, the first argument is returned.
     *
     * @see Functions#maximum()
     * @see Functions#minimum()
     * @see Functions#minimum(Comparator)
     */
    public static <T> BinaryOperator<T> maximum(Comparator<? super T> comparator) {
        requireNonNull(comparator);
        return (x, y) -> comparator.compare(x, y) >= 0 ? x : y;
    }

    /**
     * A binary operator that returns the lesser of its two arguments, according to their natural order.
     *
     * <p>If equal, the first argument is returned.
     *
     * @see Functions#maximum()
     * @see Functions#maximum(Comparator)
     * @see Functions#minimum(Comparator)
     */
    public static <T extends Comparable<? super T>> BinaryOperator<T> minimum() {
        return (x, y) -> x.compareTo(y) <= 0 ? x : y;
    }

    /**
     * A binary operator that returns the lesser of its two arguments, according to a comparator.
     *
     * <p>If equal, the first argument is returned.
     *
     * @see Functions#minimum()
     * @see Functions#maximum()
     * @see Functions#maximum(Comparator)
     */
    public static <T> BinaryOperator<T> minimum(Comparator<? super T> comparator) {
        requireNonNull(comparator);
        return (x, y) -> comparator.compare(x, y) <= 0 ? x : y;
    }

    /**
     * Invoke a callable and return the result if it completes normally, otherwise throw a {@code RuntimeException}
     * wrapping the exception thrown by the callable.
     *
     * <p>This is useful in lambda expressions when checked exceptions are not allowed. For example:
     *
     * <pre>    {@code BiFunction<Path, Charset, Stream<String>> lines = (path, charset) -> uncheck(
     *        () -> Files.lines(path, charset) // possible IOException
     *    );}</pre>
     *
     * @see Functions#uncheck(Callable, Function)
     * @see RuntimeException#RuntimeException(Throwable)
     *
     * @throws RuntimeException if the callable throws an exception
     */
    public static <T> T uncheck(Callable<T> callable) {
        return uncheck(callable, RuntimeException::new);
    }

    /**
     * Invoke a callable and return the result if it completes normally, otherwise throw an unchecked exception that is
     * a function of the exception thrown by the callable.
     *
     * <p>This is useful in lambda expressions when checked exceptions are not allowed.
     *
     * @see Functions#uncheck(Callable)
     * @see RuntimeException#RuntimeException(Throwable)
     *
     * @throws X if the callable throws an exception
     */
    public static <T, X extends RuntimeException> T uncheck(Callable<T> callable, Function<? super Exception, ? extends X> wrap) {
        requireNonNull(wrap);
        try {
            return callable.call();
        } catch (Exception exception) {
            throw wrap.apply(exception);
        }
    }
}
