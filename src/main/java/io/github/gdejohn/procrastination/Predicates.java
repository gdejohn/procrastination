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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Predicate combinators.
 *
 * <p>This class consists solely of static helper methods. It is not intended to be instantiated, so it has no public
 * constructors.
 *
 * @see Functions
 * @see Consumers
 */
public final class Predicates {
    private Predicates() {
        throw new AssertionError("this class is not intended to be instantiated");
    }

    /**
     * Safely contravariantly cast a predicate.
     *
     * @see Predicates#cast(BiPredicate)
     * @see Functions#cast(Function)
     * @see Consumers#cast(Consumer)
     */
    public static <T> Predicate<T> cast(Predicate<? super T> predicate) {
        @SuppressWarnings("unchecked") // safe because predicates are contravariant
        var narrowed = (Predicate<T>) predicate;
        return narrowed;
    }

    /**
     * Safely cast a binary predicate contravariantly with respect to each of its parameters.
     *
     * @see Predicates#cast(Predicate)
     * @see Functions#cast(BiFunction)
     * @see Consumers#cast(BiConsumer)
     */
    public static <T, U> BiPredicate<T, U> cast(BiPredicate<? super T, ? super U> predicate) {
        Objects.requireNonNull(predicate);
        @SuppressWarnings("unchecked") // safe because functions are immutable
        var widened = (BiPredicate<T, U>) predicate;
        return widened;
    }

    /**
     * Apply a predicate to an argument.
     *
     * <p>This is useful as a kind of let-expression, where the argument is an expression that does not denote a
     * variable (e.g., a method invocation, a compound expression) and the predicate is a lambda whose body refers to
     * its argument more than once.
     *
     * @see Predicates#let(Object, Object, BiPredicate)
     * @see Predicates#apply(BiPredicate, Object)
     * @see Functions#let(Object, Function)
     */
    public static <T> boolean let(T argument, Predicate<? super T> predicate) {
        return predicate.test(argument);
    }

    /**
     * Apply a binary predicate to two arguments.
     *
     * <p>This is useful as a kind of let-expression, where the arguments are expressions that do not denote variables
     * (e.g., method invocations, compound expressions) and the predicate is a lambda whose body refers to each of its
     * arguments more than once.
     *
     * @see Predicates#let(Object, Predicate)
     * @see Predicates#apply(BiPredicate, Object)
     * @see Functions#let(Object, Object, BiFunction)
     */
    public static <T, U> boolean let(T first, U second, BiPredicate<? super T, ? super U> predicate) {
        return predicate.test(first, second);
    }

    /**
     * Apply a curried predicate to two arguments.
     *
     * @see Predicates#apply(Function, Object, Object, Object)
     * @see Predicates#apply(Function, Object, Object, Object, Object)
     */
    public static <T, U> boolean apply(Function<? super T, ? extends Predicate<? super U>> predicate, T first, U second) {
        return predicate.apply(first).test(second);
    }

    /**
     * Apply a curried predicate to three arguments.
     *
     * @see Predicates#apply(Function, Object, Object)
     * @see Predicates#apply(Function, Object, Object, Object, Object)
     */
    public static <T, U, V> boolean apply(Function<? super T, ? extends Function<? super U, ? extends Predicate<? super V>>> predicate, T first, U second, V third) {
        return apply(predicate.apply(first), second, third);
    }

    /**
     * Apply a curried predicate to four arguments.
     *
     * @see Predicates#apply(Function, Object, Object)
     * @see Predicates#apply(Function, Object, Object, Object)
     */
    public static <T, U, V, W> boolean apply(Function<? super T, ? extends Function<? super U, ? extends Function<? super V, ? extends Predicate<? super W>>>> predicate, T first, U second, V third, W fourth) {
        return apply(predicate.apply(first), second, third, fourth);
    }

    /**
     * Partially apply a binary predicate, fixing its first variable.
     *
     * @see Predicates#let(Object, Predicate)
     * @see Predicates#let(Object, Object, BiPredicate)
     * @see Functions#apply(BiFunction, Object)
     */
    public static <T, U> Predicate<U> apply(BiPredicate<T, ? super U> predicate, T argument) {
        return x -> predicate.test(argument, x);
    }

    /**
     * Flipped, curried predicate application.
     *
     * <p>Given an argument, this method returns a predicate that takes a predicate and applies it to the argument.
     * {@code x -> apply(x)} is equivalent to {@code x -> f -> f.apply(x)}, or {@code flip(curry(Predicate::test))}.
     *
     * @see Predicates#flip(Function)
     * @see Predicates#curry(BiPredicate)
     * @see Predicate#test(Object)
     * @see Functions#apply(Object)
     * @see Consumers#accept(Object)
     */
    public static <T> Predicate<Predicate<T>> test(T argument) {
        return predicate -> predicate.test(argument);
    }

    /**
     * Compose a predicate with a function.
     *
     * <p>{@code compose(p,f)} is equivalent to {@code x -> p.test(f.apply(x))}.
     *
     * @see Predicates#compose(BiPredicate, Function)
     * @see Predicates#on(BiPredicate, Function)
     * @see Functions#compose(Function, Function)
     * @see Consumers#compose(Consumer, Function)
     */
    public static <T, U> Predicate<T> compose(Predicate<? super U> predicate, Function<? super T, ? extends U> function) {
        return argument -> predicate.test(function.apply(argument));
    }

    /**
     * A binary predicate that applies a function to its first argument and passes the result and its second argument
     * to another binary predicate.
     *
     * <p>{@code compose(p,f)} is equivalent to {@code (x,y) -> p.test(f.apply(x),y)}.
     *
     * @see Predicates#compose(Predicate, Function)
     * @see Predicates#on(BiPredicate, Function)
     * @see Functions#compose(BiFunction, Function)
     * @see Consumers#compose(BiConsumer, Function)
     */
    public static <T, U, V> BiPredicate<T, V> compose(BiPredicate<? super U, ? super V> predicate, Function<? super T, ? extends U> function) {
        return (x, y) -> predicate.test(function.apply(x), y);
    }

    /**
     * A binary predicate that applies a function to both of its arguments and passes the results to another binary
     * predicate.
     *
     * <p>{@code on(p,f)} is equivalent to {@code (x,y) -> p.test(f.apply(x),f.apply(y))}.
     *
     * @see Predicates#compose(Predicate, Function)
     * @see Predicates#compose(BiPredicate, Function)
     * @see Functions#on(BiFunction, Function)
     * @see Consumers#on(BiConsumer, Function)
     */
    public static <T, R> BiPredicate<T, T> on(BiPredicate<? super R, ? super R> predicate, Function<? super T, ? extends R> function) {
        return (x, y) -> predicate.test(function.apply(x), function.apply(y));
    }

    /** Reverse the order of the parameters of a binary predicate. */
    public static <T, U> BiPredicate<U, T> flip(BiPredicate<? super T, ? super U> predicate) {
        return (x, y) -> predicate.test(y, x);
    }

    /** Reverse the order of the parameters of a curried predicate. */
    public static <T, U> Function<U, Predicate<T>> flip(Function<? super T, ? extends Predicate<? super U>> predicate) {
        return x -> y -> predicate.apply(y).test(x);
    }

    /**
     * Combine the parameters of a binary predicate, yielding a predicate of pairs.
     *
     * @see Predicates#spread(Predicate)
     * @see Predicates#uncurry(Function)
     * @see Predicates#curry(BiPredicate)
     * @see Functions#gather(BiFunction)
     * @see Consumers#gather(BiConsumer)
     */
    public static <T, U> Predicate<Pair<T, U>> gather(BiPredicate<? super T, ? super U> predicate) {
        return pair -> pair.match(predicate::test);
    }

    /**
     * Separate the parameter of a predicate of pairs, yielding a binary predicate.
     *
     * @see Predicates#gather(BiPredicate)
     * @see Predicates#curry(BiPredicate)
     * @see Predicates#uncurry(Function)
     * @see Functions#spread(Function)
     * @see Consumers#spread(Consumer)
     */
    public static <T, U> BiPredicate<T, U> spread(Predicate<? super Pair<T, U>> predicate) {
        return (x, y) -> predicate.test(Pair.of(x, y));
    }

    /**
     * Adapt a binary predicate into a function returning a predicate.
     *
     * @see Predicates#uncurry(Function)
     * @see Predicates#spread(Predicate)
     * @see Predicates#gather(BiPredicate)
     * @see Functions#curry(BiFunction)
     * @see Consumers#curry(BiConsumer)
     */
    public static <T, U> Function<T, Predicate<U>> curry(BiPredicate<? super T, ? super U> predicate) {
        return x -> y -> predicate.test(x, y);
    }

    /**
     * Adapt a function returning a predicate into a binary predicate.
     *
     * @see Predicates#curry(BiPredicate)
     * @see Predicates#gather(BiPredicate)
     * @see Predicates#spread(Predicate)
     * @see Functions#uncurry(Function)
     * @see Consumers#uncurry(Function)
     */
    public static <T, U> BiPredicate<T, U> uncurry(Function<? super T, ? extends Predicate<? super U>> predicate) {
        return (x, y) -> predicate.apply(x).test(y);
    }

    /**
     * Return the fixed point of a unary operator on predicates, enabling recursive lambda expressions (i.e., anonymous
     * recursion).
     *
     * @see Functions#fix(UnaryOperator)
     * @see Consumers#fix(UnaryOperator)
     */
    public static <T> Predicate<T> fix(UnaryOperator<Predicate<T>> predicate) {
        class Fix implements Predicate<T> {
            private final Predicate<T> predicate;

            Fix(UnaryOperator<Predicate<T>> predicate) {
                this.predicate = predicate.apply(this);
            }

            @Override
            public boolean test(T argument) {
                return this.predicate.test(argument);
            }
        }

        return new Fix(predicate);
    }

    /**
     * A predicate that delegates to a binary predicate, duplicating its argument.
     *
     * <p>{@code p -> join(p)} is equivalent to {@code p -> x -> p.test(x, x)}.
     *
     * @see Predicates#join(Function)
     */
    public static <T> Predicate<T> join(BiPredicate<? super T, ? super T> predicate) {
        return argument -> predicate.test(argument, argument);
    }

    /**
     * A predicate that delegates to a curried predicate, duplicating its argument.
     *
     * <p>{@code p -> join(p)} is equivalent to {@code p -> x -> p.apply(x).test(x)}.
     *
     * @see Predicates#join(BiPredicate)
     */
    public static <T> Predicate<T> join(Function<? super T, Predicate<? super T>> predicate) {
        return argument -> apply(predicate, argument, argument);
    }

    /**
     * A predicate that always ignores its arguments and returns the same value.
     *
     * <p>{@code constant(true)} represents a tautology: true regardless of input.
     * {@code constant(false)} represents a contradiction: false regardless of input.
     *
     * @see Predicates#not(Predicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     */
    public static Predicate<Object> constant(boolean value) {
        return value ? Predicates.TRUE : Predicates.FALSE;
    }

    private static final Predicate<Object> TRUE = argument -> true;

    private static final Predicate<Object> FALSE = argument -> false;

    /**
     * The negation of a predicate.
     *
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> not(Predicate<? super T> predicate) {
        return argument -> !predicate.test(argument);
    }

    /**
     * The disjunction of two predicates: at least one of {@code p} and {@code q}.
     *
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> or(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> p.test(argument) || q.test(argument);
    }

    /**
     * The conjunction of two predicates: both {@code p} and {@code q}.
     *
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> and(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> p.test(argument) && q.test(argument);
    }

    /**
     * The exclusive disjunction of two predicates: either {@code p} or {@code q}, but not both.
     *
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> xor(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> p.test(argument) ^ q.test(argument);
    }

    /**
     * The negated disjunction (joint denial) of two predicates: neither {@code p} nor {@code q}.
     *
     * @see Predicates#not(Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> nor(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> !(p.test(argument) || q.test(argument));
    }

    /**
     * The negated conjunction (alternative denial) of two predicates: at most one of {@code p} and {@code q}.
     *
     * @see Predicates#not(Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> nand(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> !(p.test(argument) && q.test(argument));
    }

    /**
     * Implication: if {@code p} then {@code q}.
     *
     * <p>Equivalently: (not {@code p}) or {@code q}. Also known as the material conditional. {@code implies(p,q)} is
     * equivalent to {@code impliedBy(q,p)}. Both are offered because neither is commutative and they take advantage of
     * short-circuiting, evaluating their given predicates in parameter order.
     *
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> implies(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> (!p.test(argument)) || q.test(argument);
    }

    /**
     * Converse implication: {@code p} if {@code q}.
     *
     * <p>Equivalently: {@code p} or not {@code q}. {@code impliedBy(p,q)} is equivalent to {@code implies(q,p)}. Both
     * are offered because neither is commutative and they take advantage of short-circuiting, evaluating their given
     * predicates in parameter order.
     *
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#iff(Predicate, Predicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#constant(boolean)
     */
    public static <T> Predicate<T> impliedBy(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> p.test(argument) || !q.test(argument);
    }

    /**
     * The negated exclusive disjunction (biconditional, xnor) of two predicates: {@code p} if and only if {@code q}.
     *
     * <p>Equivalently:
     *
     * <ul>
     * <li>{@code p == q}
     * <li>not ({@code p} xor {@code q})
     * <li>({@code p} implies {@code q}) and ({@code q} implies {@code p})
     * </ul>
     *
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     */
    public static <T> Predicate<T> iff(Predicate<? super T> p, Predicate<? super T> q) {
        return argument -> p.test(argument) == q.test(argument);
    }

    /**
     * The negation of a binary predicate.
     *
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#not(Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> not(BiPredicate<? super T, ? super U> predicate) {
        return (x, y) -> !predicate.test(x, y);
    }

    /**
     * The disjunction of two binary predicates: at least one of {@code p} and {@code q}.
     *
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#or(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> or(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> p.test(x, y) || q.test(x, y);
    }

    /**
     * The conjunction of two binary predicates: both {@code p} and {@code q}.
     *
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#and(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> and(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> p.test(x, y) && q.test(x, y);
    }

    /**
     * The exclusive disjunction of two binary predicates: either {@code p} or {@code q}, but not both.
     *
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#xor(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> xor(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> p.test(x, y) ^ q.test(x, y);
    }

    /**
     * The negated disjunction (joint denial) of two binary predicates: neither {@code p} nor {@code q}.
     *
     * @see Predicates#not(BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#nor(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> nor(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> !(p.test(x, y) || q.test(x, y));
    }

    /**
     * The negated conjunction (alternative denial) of two binary predicates: at most one of {@code p} and {@code q}.
     *
     * @see Predicates#not(BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#nand(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> nand(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> !(p.test(x, y) && q.test(x, y));
    }

    /**
     * Connect two binary predicates by implication: if {@code p} then {@code q}.
     *
     * <p>Equivalently: (not {@code p}) or {@code q}. Also known as the material conditional. {@code implies(p,q)} is
     * equivalent to {@code impliedBy(q,p)}. Both are offered because neither is commutative and they take advantage of
     * short-circuiting, evaluating their given predicates in parameter order.
     *
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#implies(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> implies(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> (!p.test(x, y)) || q.test(x, y);
    }

    /**
     * Connect two binary predicates by converse implication: {@code p} if {@code q}.
     *
     * <p>Equivalently: {@code p} or not {@code q}. {@code impliedBy(p,q)} is equivalent to {@code implies(q,p)}. Both
     * are offered because neither is commutative and they take advantage of short-circuiting, evaluating their given
     * predicates in parameter order.
     *
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#iff(BiPredicate, BiPredicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(Predicate, Predicate)
     * @see Predicates#constant(boolean)
     */
    public static <T, U> BiPredicate<T, U> impliedBy(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> p.test(x, y) || !q.test(x, y);
    }

    /**
     * The negated exclusive disjunction (biconditional, xnor) of two binary predicates: {@code p} if and only if
     * {@code q}.
     *
     * <p>Equivalently:
     *
     * <ul>
     * <li>{@code p == q}
     * <li>not ({@code p} xor {@code q})
     * <li>({@code p} implies {@code q}) and ({@code q} implies {@code p})
     * </ul>
     *
     * @see Predicates#implies(BiPredicate, BiPredicate)
     * @see Predicates#impliedBy(BiPredicate, BiPredicate)
     * @see Predicates#not(BiPredicate)
     * @see Predicates#xor(BiPredicate, BiPredicate)
     * @see Predicates#nor(BiPredicate, BiPredicate)
     * @see Predicates#or(BiPredicate, BiPredicate)
     * @see Predicates#and(BiPredicate, BiPredicate)
     * @see Predicates#nand(BiPredicate, BiPredicate)
     * @see Predicates#iff(Predicate, Predicate)
     */
    public static <T, U> BiPredicate<T, U> iff(BiPredicate<? super T, ? super U> p, BiPredicate<? super T, ? super U> q) {
        return (x, y) -> p.test(x, y) == q.test(x, y);
    }

    /** A predicate that tests if its argument is equal to some object according to a natural order. */
    public static <T extends Comparable<? super T>> Predicate<T> equalTo(T object) {
        return argument -> argument.compareTo(object) == 0;
    }

    /** A predicate that tests if its argument is equal to some object according to a comparator. */
    public static <T> Predicate<T> equalTo(T object, Comparator<? super T> comparator) {
        return argument -> comparator.compare(argument, object) == 0;
    }

    /** A predicate that tests if its argument is strictly less than some object according to a natural order. */
    public static <T extends Comparable<? super T>> Predicate<T> lessThan(T object) {
        return argument -> argument.compareTo(object) < 0;
    }

    /** A predicate that tests if its argument is strictly less than some object according to a comparator. */
    public static <T> Predicate<T> lessThan(T object, Comparator<? super T> comparator) {
        return argument -> comparator.compare(argument, object) < 0;
    }

    /** A predicate that tests if its argument is less than or equal to some object according to a natural order. */
    public static <T extends Comparable<? super T>> Predicate<T> lessThanOrEqualTo(T object) {
        return argument -> argument.compareTo(object) <= 0;
    }

    /** A predicate that tests if its argument is less than or equal to some object according to a comparator. */
    public static <T> Predicate<T> lessThanOrEqualTo(T object, Comparator<? super T> comparator) {
        return argument -> comparator.compare(argument, object) <= 0;
    }

    /** A predicate that tests if its argument is strictly greater than some object according to a natural order. */
    public static <T extends Comparable<? super T>> Predicate<T> greaterThan(T object) {
        return argument -> argument.compareTo(object) > 0;
    }

    /** A predicate that tests if its argument is strictly greater than some object according to a comparator. */
    public static <T> Predicate<T> greaterThan(T object, Comparator<? super T> comparator) {
        return argument -> comparator.compare(argument, object) > 0;
    }

    /** A predicate that tests if its argument is greater than or equal to some object according to a natural order. */
    public static <T extends Comparable<? super T>> Predicate<T> greaterThanOrEqualTo(T object) {
        return argument -> argument.compareTo(object) >= 0;
    }

    /** A predicate that tests if its argument is greater than or equal to some object according to a comparator. */
    public static <T> Predicate<T> greaterThanOrEqualTo(T object, Comparator<? super T> comparator) {
        return argument -> comparator.compare(argument, object) >= 0;
    }

    /** The equivalence relation imposed by a natural order. */
    public static <T extends Comparable<? super T>> BiPredicate<T, T> equal() {
        return (x, y) -> x.compareTo(y) == 0;
    }

    /** The equivalence relation imposed by a comparator. */
    public static <T> BiPredicate<T, T> equal(Comparator<? super T> comparator) {
        return (x, y) -> comparator.compare(x, y) == 0;
    }

    /** The binary relation {@code <} imposed by a natural order. */
    public static <T extends Comparable<? super T>> BiPredicate<T, T> strictlyIncreasing() {
        return (x, y) -> x.compareTo(y) < 0;
    }

    /** The binary relation {@code <} imposed by a comparator. */
    public static <T> BiPredicate<T, T> strictlyIncreasing(Comparator<? super T> comparator) {
        return (x, y) -> comparator.compare(x, y) < 0;
    }

    /** The binary relation {@code <=} imposed by a natural order. */
    public static <T extends Comparable<? super T>> BiPredicate<T, T> increasing() {
        return (x, y) -> x.compareTo(y) <= 0;
    }

    /** The binary relation {@code <=} imposed by a comparator. */
    public static <T> BiPredicate<T, T> increasing(Comparator<? super T> comparator) {
        return (x, y) -> comparator.compare(x, y) <= 0;
    }

    /** The binary relation {@code >} imposed by a natural order. */
    public static <T extends Comparable<? super T>> BiPredicate<T, T> strictlyDecreasing() {
        return (x, y) -> x.compareTo(y) > 0;
    }

    /** The binary relation {@code >} imposed by a comparator. */
    public static <T> BiPredicate<T, T> strictlyDecreasing(Comparator<? super T> comparator) {
        return (x, y) -> comparator.compare(x, y) > 0;
    }

    /** The binary relation {@code >=} imposed by a natural order. */
    public static <T extends Comparable<? super T>> BiPredicate<T, T> decreasing() {
        return (x, y) -> x.compareTo(y) >= 0;
    }

    /** The binary relation {@code >=} imposed by a comparator. */
    public static <T> BiPredicate<T, T> decreasing(Comparator<? super T> comparator) {
        return (x, y) -> comparator.compare(x, y) >= 0;
    }

    /** A predicate that tests if its argument is a factor of the given long dividend. */
    public static Predicate<Long> divides(long dividend) {
        return divisor -> dividend % divisor == 0;
    }

    /** A predicate that tests if its argument is a factor of the given int dividend. */
    public static Predicate<Integer> divides(int dividend) {
        return divisor -> dividend % divisor == 0;
    }

    /** A predicate that tests if its argument is a factor of the given short dividend. */
    public static Predicate<Short> divides(short dividend) {
        return divisor -> dividend % divisor == 0;
    }

    /** A predicate that tests if its argument is a factor of the given byte dividend. */
    public static Predicate<Byte> divides(byte dividend) {
        return divisor -> dividend % divisor == 0;
    }
}
