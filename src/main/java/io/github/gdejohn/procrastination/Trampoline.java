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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.github.gdejohn.procrastination.Functions.apply;
import static io.github.gdejohn.procrastination.Functions.fix;
import static io.github.gdejohn.procrastination.Unit.unit;

/**
 * Stack-safe tail recursion via tail-call elimination.
 *
 * <p>Use the static factory methods {@link Trampoline#call(Supplier) call} and
 * {@link Trampoline#terminate(Object) terminate} to create trampolines, and the instance method
 * {@link Trampoline#evaluate() evaluate} to get the result.
 *
 * <p>To trampoline a tail-recursive method with some return type {@code T},
 *
 * <ol>
 * <li>change the return type to {@code Trampoline<T>};
 * <li>for each base case, wrap the returned expression with {@code terminate()};
 * <li>suspend each recursive call in a {@link Supplier} lambda expression and wrap with {@code call()}.
 * </ol>
 *
 * <p>This approach cleanly extends to mutual recursion.
 *
 * <p>Instead of delegating to a private trampolined helper method to avoid exposing to client code the implementation
 * details of tail recursion and trampolining, tail-recursive computations can be performed inline with the static
 * helper methods {@link Trampoline#evaluate(Object, UnaryOperator)} and
 * {@link Trampoline#execute(Object, UnaryOperator)}.
 *
 * @param <T> the type of the value that this trampoline computes
 */
public abstract class Trampoline<T> {
    private Trampoline() {}

    protected abstract Trampoline<? extends T> bounce();

    protected boolean finished() {
        return false;
    }

    /**
     * The result of the computation that this trampoline represents.
     *
     * <p>This method runs in constant stack space.
     */
    public T evaluate() {
        Trampoline<? extends T> trampoline = this;
        do {
            trampoline = trampoline.bounce();
        } while (!trampoline.finished());
        return trampoline.evaluate();
    }

    /**
     * A trampoline that just returns a given value when evaluated.
     *
     * <p>In the context of tail recursion, this represents a base case.
     */
    public static <T> Trampoline<T> terminate(T value) {
        Objects.requireNonNull(value);
        return new Trampoline<>() {
            @Override
            protected Trampoline<? extends T> bounce() {
                throw new AssertionError("terminal trampolines should never be bounced");
            }

            @Override
            protected boolean finished() {
                return true;
            }

            @Override
            public T evaluate() {
                return value;
            }
        };
    }

    /**
     * A trampoline that just returns immediately when evaluated, producing nothing useful.
     *
     * <p>This is analogous to returning from a void method. In the context of tail recursion, this represents a base
     * case.
     */
    public static Trampoline<Unit> terminate() {
        return Trampoline.UNIT;
    }

    private static final Trampoline<Unit> UNIT = terminate(unit());

    /**
     * A suspended tail call.
     *
     * <p>In the context of tail recursion, this represents a recursive case.
     *
     * @see Trampoline#terminate(Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate()
     * @see Trampoline#call(Function, Object)
     * @see Trampoline#call(Function, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object, Object)
     */
    public static <T> Trampoline<T> call(Supplier<? extends Trampoline<? extends T>> trampoline) {
        return new Trampoline<>() {
            @Override
            protected Trampoline<? extends T> bounce() {
                return trampoline.get();
            }
        };
    }

    /**
     * A suspended tail call with one parameter.
     *
     * <p>In the context of tail recursion, this represents a recursive case. This method is useful in conjunction with
     * the static method {@link Trampoline#evaluate(Object, UnaryOperator) evaluate()} for the purpose of recursive
     * lambdas. To trampoline a tail-recursive method, {@link Trampoline#call(Supplier) call(Supplier)} is better
     * suited.
     *
     * @see Trampoline#terminate(Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, UnaryOperator)
     * @see Trampoline#execute(Object, UnaryOperator)
     * @see Trampoline#call(Function, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object, Object)
     * @see Trampoline#call(Supplier)
     */
    public static <T, R> Trampoline<R> call(Function<? super T, ? extends Trampoline<? extends R>> function, T argument) {
        return new Trampoline<>() {
            @Override
            protected Trampoline<? extends R> bounce() {
                return function.apply(argument);
            }
        };
    }

    /**
     * A suspended, curried tail call with two parameters.
     *
     * <p>In the context of tail recursion, this represents a recursive case.
     *
     * @see Trampoline#terminate(Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, UnaryOperator)
     * @see Trampoline#call(Function, Object)
     * @see Trampoline#call(Function, Object, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object, Object)
     * @see Trampoline#call(Supplier)
     */
    public static <T, U, R> Trampoline<R> call(Function<? super T, ? extends Function<? super U, ? extends Trampoline<? extends R>>> function, T first, U second) {
        return new Trampoline<>() {
            @Override
            protected Trampoline<? extends R> bounce() {
                return apply(function, first, second);
            }
        };
    }

    /**
     * A suspended, curried tail call with three parameters.
     *
     * <p>In the context of tail recursion, this represents a recursive case.
     *
     * @see Trampoline#terminate(Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, UnaryOperator)
     * @see Trampoline#call(Function, Object)
     * @see Trampoline#call(Function, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object, Object)
     * @see Trampoline#call(Supplier)
     */
    public static <T, U, V, R> Trampoline<R> call(Function<? super T, ? extends Function<? super U, ? extends Function<? super V, ? extends Trampoline<? extends R>>>> function, T first, U second, V third) {
        return new Trampoline<>() {
            @Override
            protected Trampoline<? extends R> bounce() {
                return apply(function, first, second, third);
            }
        };
    }

    /**
     * A suspended, curried tail call with four parameters.
     *
     * <p>In the context of tail recursion, this represents a recursive case.
     *
     * @see Trampoline#terminate(Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, Object, Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, Object, UnaryOperator)
     * @see Trampoline#call(Function, Object)
     * @see Trampoline#call(Function, Object, Object)
     * @see Trampoline#call(Function, Object, Object, Object)
     * @see Trampoline#call(Supplier)
     */
    public static <T, U, V, W, R> Trampoline<R> call(Function<? super T, ? extends Function<? super U, ? extends Function<? super V, ? extends Function<? super W, ? extends Trampoline<? extends R>>>>> function, T first, U second, V third, W fourth) {
        return new Trampoline<>() {
            @Override
            protected Trampoline<? extends R> bounce() {
                return apply(function, first, second, third, fourth);
            }
        };
    }

    /**
     * Apply a trampolined, recursive function to an argument.
     *
     * <p>This enables stack-safe, inline recursion. For example:
     *
     * <pre>    {@code static <T> Maybe<T> find(Sequence<T> sequence, Predicate<T> predicate) {
     *        return Trampoline.evaluate(
     *            sequence,
     *            f -> seq -> seq.match(
     *                (head, tail) -> predicate.test(head) ? terminate(Maybe.of(head)) : call(f, tail),
     *                () -> terminate(Maybe.empty())
     *            )
     *        );
     *    }}</pre>
     *
     * <p>This takes full advantage of type inference and makes a private helper method unnecessary.
     *
     * @see Trampoline#call(Function, Object)
     * @see Trampoline#terminate(Object)
     * @see Trampoline#execute(Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, Object, Object, UnaryOperator)
     */
    public static <T, R> R evaluate(T argument, UnaryOperator<Function<T, Trampoline<R>>> function) {
        return fix(function).apply(argument).evaluate();
    }

    /**
     * Apply a curried, trampolined, recursive function to two arguments.
     *
     * <p>For example:
     *
     * <pre>    {@code static int length(Sequence<?> sequence) {
     *        return Trampoline.evaluate(
     *            sequence,
     *            0,
     *            f -> seq -> n -> seq.match(
     *                (head, tail) -> call(f, tail, n + 1),
     *                () -> terminate(n)
     *            )
     *        );
     *    }}</pre>
     *
     * @see Trampoline#call(Function, Object, Object)
     * @see Trampoline#terminate(Object)
     * @see Trampoline#execute(Object, Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#evaluate(Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, Object, Object, UnaryOperator)
     */
    public static <T, U, R> R evaluate(T first, U second, UnaryOperator<Function<T, Function<U, Trampoline<R>>>> function) {
        return apply(fix(function), first, second).evaluate();
    }

    /**
     * Apply a curried, trampolined, recursive function to three arguments.
     *
     * @see Trampoline#call(Function, Object, Object, Object)
     * @see Trampoline#terminate(Object)
     * @see Trampoline#execute(Object, Object, Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#evaluate(Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, Object, Object, UnaryOperator)
     */
    public static <T, U, V, R> R evaluate(T first, U second, V third, UnaryOperator<Function<T, Function<U, Function<V, Trampoline<R>>>>> function) {
        return apply(fix(function), first, second, third).evaluate();
    }

    /**
     * Apply a curried, trampolined, recursive function to four arguments.
     *
     * @see Trampoline#call(Function, Object, Object, Object, Object)
     * @see Trampoline#terminate(Object)
     * @see Trampoline#execute(Object, Object, Object, Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#evaluate(Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, UnaryOperator)
     * @see Trampoline#evaluate(Object, Object, Object, UnaryOperator)
     */
    public static <T, U, V, W, R> R evaluate(T first, U second, V third, W fourth, UnaryOperator<Function<T, Function<U, Function<V, Function<W, Trampoline<R>>>>>> function) {
        return apply(fix(function), first, second, third, fourth).evaluate();
    }

    /**
     * Apply a trampolined, recursive action to an argument.
     *
     * <p>This enables stack-safe, inline recursion. For example:
     *
     * <pre>    {@code static <T> void forEach(Sequence<T> sequence, Consumer<T> action) {
     *        Trampoline.execute(
     *            sequence,
     *            f -> seq -> seq.match(
     *                (head, tail) -> {
     *                    action.accept(head);
     *                    return call(f, tail);
     *                },
     *                () -> terminate()
     *            )
     *        );
     *    }}</pre>
     *
     * <p>This takes full advantage of type inference and makes a private helper method unnecessary.
     *
     * @see Trampoline#call(Function, Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#execute(Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, Object, UnaryOperator)
     */
    public static <T> void execute(T argument, UnaryOperator<Function<T, Trampoline<Unit>>> action) {
        fix(action).apply(argument).evaluate();
    }

    /**
     * Apply a curried, trampolined, recursive action to two arguments.
     *
     * @see Trampoline#call(Function, Object, Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#execute(Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, Object, UnaryOperator)
     */
    public static <T, U> void execute(T first, U second, UnaryOperator<Function<T, Function<U, Trampoline<Unit>>>> action) {
        apply(fix(action), first, second).evaluate();
    }

    /**
     * Apply a curried, trampolined, recursive action to three arguments.
     *
     * @see Trampoline#call(Function, Object, Object, Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, Object, Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#execute(Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, Object, UnaryOperator)
     */
    public static <T, U, V> void execute(T first, U second, V third, UnaryOperator<Function<T, Function<U, Function<V, Trampoline<Unit>>>>> action) {
        apply(fix(action), first, second, third).evaluate();
    }

    /**
     * Apply a curried, trampolined, recursive action to four arguments.
     *
     * @see Trampoline#call(Function, Object, Object, Object, Object)
     * @see Trampoline#terminate()
     * @see Trampoline#evaluate(Object, Object, Object, Object, UnaryOperator)
     * @see Functions#fix(UnaryOperator)
     * @see Trampoline#execute(Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, UnaryOperator)
     * @see Trampoline#execute(Object, Object, Object, UnaryOperator)
     */
    public static <T, U, V, W> void execute(T first, U second, V third, W fourth, UnaryOperator<Function<T, Function<U, Function<V, Function<W, Trampoline<Unit>>>>>> action) {
        apply(fix(action), first, second, third, fourth).evaluate();
    }
}
