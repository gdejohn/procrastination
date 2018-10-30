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
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.gdejohn.procrastination.Functions.let;
import static io.github.gdejohn.procrastination.Unit.unit;
import static java.util.Objects.requireNonNull;

/**
 * A lazily evaluated, persistent, ordered, heterogeneous collection with exactly two elements, allowing duplicates (a
 * 2-tuple).
 *
 * <p>Whereas {@link Either} represents the sum of two types, {@link Pair} represents the product of two types (and
 * {@link Unit} represents the product of zero types).
 *
 * @param <T> the type of the first element
 * @param <U> the type of the second element
 */
public abstract class Pair<T, U> {
    private static abstract class Proxy<T, U> extends Pair<T, U> {
        protected Proxy() {}

        protected abstract Pair<T, U> principal();

        @Override
        public <R> R match(BiFunction<? super T, ? super U, ? extends R> function) {
            return this.principal().match(function);
        }

        @Override
        public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Supplier<U>, ? extends R> function) {
            return this.principal().matchLazy(function);
        }
    }

    private Pair() {}

    /**
     * A lazily evaluated pair.
     *
     * @see Pair#of(Object,Object)
     * @see Pair#of(Object,Supplier)
     * @see Pair#of(Supplier,Object)
     * @see Pair#of(Supplier,Supplier)
     */
    public static <T, U> Pair<T, U> lazy(Supplier<? extends Pair<? extends T, ? extends U>> pair) {
        requireNonNull(pair);
        return new Pair.Proxy<>() {
            @Override
            protected Pair<T, U> principal() {
                return cast(pair.get().memoize());
            }

            @Override
            public Pair<T, U> memoize() {
                return Pair.memoize(this);
            }
        };
    }

    private static <T, U> Pair<T, U> memoize(Proxy<T, U> pair) {
        var principal = Functions.memoize(pair::principal);
        return new Pair.Proxy<>() {
            @Override
            protected Pair<T, U> principal() {
                return principal.get();
            }
        };
    }

    /**
     * A pair of eagerly evaluated elements.
     *
     * @see Pair#of(Object,Supplier)
     * @see Pair#of(Supplier,Object)
     * @see Pair#of(Supplier,Supplier)
     * @see Pair#lazy(Supplier)
     * @see Pair#duplicate(Object)
     */
    public static <T, U> Pair<T, U> of(T first, U second) {
        requireNonNull(first);
        requireNonNull(second);
        return new Pair<>() {
            @Override
            public <R> R match(BiFunction<? super T, ? super U, ? extends R> function) {
                return function.apply(first, second);
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Supplier<U>, ? extends R> function) {
                return function.apply((Supplier<T>) () -> first, (Supplier<U>) () -> second);
            }

            @Override
            public Pair<T, U> eager() {
                return this;
            }
        };
    }

    /**
     * A pair with an eagerly evaluated first element and a lazily evaluated second element.
     *
     * @see Pair#of(Object,Object)
     * @see Pair#of(Supplier,Object)
     * @see Pair#of(Supplier,Supplier)
     * @see Pair#lazy(Supplier)
     */
    public static <T, U> Pair<T, U> of(T first, Supplier<? extends U> second) {
        requireNonNull(first);
        requireNonNull(second);
        return new Pair<>() {
            @Override
            public <R> R match(BiFunction<? super T, ? super U, ? extends R> function) {
                return function.apply(first, requireNonNull(second.get()));
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Supplier<U>, ? extends R> function) {
                return function.apply((Supplier<T>) () -> first, Functions.memoize(second));
            }

            @Override
            public Pair<T, U> memoize() {
                var memoized = Functions.memoize(second);
                return memoized == second ? this : Pair.of(first, memoized);
            }
        };
    }

    /**
     * A pair with a lazily evaluated first element and an eagerly evaluated second element.
     *
     * @see Pair#of(Object,Object)
     * @see Pair#of(Object,Supplier)
     * @see Pair#of(Supplier,Supplier)
     * @see Pair#lazy(Supplier)
     */
    public static <T, U> Pair<T, U> of(Supplier<? extends T> first, U second) {
        requireNonNull(first);
        requireNonNull(second);
        return new Pair<>() {
            @Override
            public <R> R match(BiFunction<? super T, ? super U, ? extends R> function) {
                return function.apply(requireNonNull(first.get()), second);
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Supplier<U>, ? extends R> function) {
                return function.apply(Functions.memoize(first), (Supplier<U>) () -> second);
            }

            @Override
            public Pair<T, U> memoize() {
                var memoized = Functions.memoize(first);
                return memoized == first ? this : Pair.of(memoized, second);
            }
        };
    }

    /**
     * A pair of lazily evaluated elements.
     *
     * @see Pair#of(Object,Object)
     * @see Pair#of(Object,Supplier)
     * @see Pair#of(Supplier,Object)
     * @see Pair#lazy(Supplier)
     * @see Pair#duplicate(Supplier)
     */
    public static <T, U> Pair<T, U> of(Supplier<? extends T> first, Supplier<? extends U> second) {
        requireNonNull(first);
        requireNonNull(second);
        return new Pair<>() {
            @Override
            public <R> R match(BiFunction<? super T, ? super U, ? extends R> function) {
                return function.apply(requireNonNull(first.get()), requireNonNull(second.get()));
            }

            @Override
            public <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Supplier<U>, ? extends R> function) {
                return function.apply(Functions.memoize(first), Functions.memoize(second));
            }

            @Override
            public Pair<T, U> memoize() {
                var x = Functions.memoize(first);
                var y = Functions.memoize(second);
                return x == first && y == second ? this : Pair.of(x, y);
            }
        };
    }

    /** A lazy view of a map entry as a pair. */
    public static <T, U> Pair<T, U> from(Map.Entry<? extends T, ? extends U> entry) {
        requireNonNull(entry);
        return Pair.of(entry::getKey, entry::getValue);
    }

    /**
     * A pair where both elements are the same value.
     *
     * @see Pair#of(Object,Object)
     */
    public static <T> Pair<T, T> duplicate(T value) {
        requireNonNull(value);
        return Pair.of(value, value);
    }

    /**
     * A pair where both elements are the same lazy value.
     *
     * @see Pair#of(Supplier,Supplier)
     */
    public static <T> Pair<T, T> duplicate(Supplier<? extends T> value) {
        requireNonNull(value);
        return Pair.lazy(() -> let(Functions.memoize(value), memoized -> Pair.of(memoized, memoized)));
    }

    /** Safe covariant cast. */
    public static <T, U> Pair<T, U> cast(Pair<? extends T, ? extends U> pair) {
        requireNonNull(pair);
        @SuppressWarnings("unchecked") // safe because pairs are immutable
        var widened = (Pair<T, U>) pair;
        return widened;
    }

    /**
     * Perform an action on each element of a pair.
     *
     * @see Pair#forBoth(BiConsumer)
     */
    public static <T> void forEach(Pair<? extends T, ? extends T> pair, Consumer<? super T> action) {
        pair.forBoth(
            (first, second) -> {
                action.accept(first);
                action.accept(second);
            }
        );
    }

    /**
     * Apply to each element a function that accepts a common supertype of the two elements.
     *
     * @see Pair#mapBoth(Function,Function)
     * @see Pair#mapFirst(Function)
     * @see Pair#mapSecond(Function)
     */
    public static <T, R> Pair<R, R> map(Pair<? extends T, ? extends T> pair, Function<? super T, ? extends R> function) {
        return pair.mapBoth(function, function);
    }

    /** Order pairs by the natural order of the type of their first elements. */
    public static <T extends Comparable<? super T>> Comparator<Pair<T, ?>> byFirst() {
        return Comparator.comparing(Pair::first);
    }

    /** Order pairs by a given order on the type of their first elements. */
    public static <T> Comparator<Pair<T, ?>> byFirst(Comparator<? super T> comparator) {
        return Comparator.comparing(Pair::first, comparator);
    }

    /** Order pairs by the natural order of the type of their second elements. */
    public static <U extends Comparable<? super U>> Comparator<Pair<?, U>> bySecond() {
        return Comparator.comparing(Pair::second);
    }

    /** Order pairs by a given order on the type of their second elements. */
    public static <U> Comparator<Pair<?, U>> bySecond(Comparator<? super U> comparator) {
        return Comparator.comparing(Pair::second, comparator);
    }

    /**
     * Return a value defined in terms of the two eagerly evaluated elements of this pair.
     *
     * <p>This method simulates pattern matching on this pair, forcing evaluation of its elements. The first element is
     * passed as the first argument to the given binary function, the second element is passed as the second argument,
     * and the result is returned.
     *
     * @param <R> the type of the resulting value
     */
    public abstract <R> R match(BiFunction<? super T, ? super U, ? extends R> function);

    /**
     * Return a value defined in terms of the two lazily evaluated elements of this pair.
     *
     * <p>This method simulates pattern matching on this pair, deferring evaluation of its elements. A supplier of the
     * first element is passed as the first argument to the given binary function, a supplier of the second element is
     * passed as the second argument, and the result is returned.
     *
     * <p>In contrast to {@link Pair#match(BiFunction) Pair.match()}, this method is lazy with respect to the elements
     * of this pair. The caller of this method decides if and when to force evaluation of the elements. This is useful,
     * for example, to preserve the laziness of an underlying pair in terms of which another value is lazily defined.
     *
     * @param <R> the type of the resulting value
     */
    public abstract <R> R matchLazy(BiFunction<? super Supplier<T>, ? super Supplier<U>, ? extends R> function);

    /**
     * Perform a binary action on the elements of this pair.
     *
     * @see Pair#forEach(Pair,Consumer)
     */
    public void forBoth(BiConsumer<? super T, ? super U> action) {
        this.match(
            (first, second) -> {
                action.accept(first, second);
                return unit();
            }
        );
    }

    /**
     * A pair that only computes each element at most once, the first time it is asked for, delegating to this pair and
     * caching the result.
     */
    public Pair<T, U> memoize() {
        return this;
    }

    /** Eagerly evaluate the elements of this pair and return the results as a new pair. */
    public Pair<T, U> eager() {
        return this.match(Pair::of);
    }

    /** This pair as a map entry. */
    public Map.Entry<T, U> entry() {
        return this.match(Map::entry);
    }

    /**
     * True if and only if the argument is a pair and contains exactly the same elements as this pair in the same
     * order.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof Pair) {
            return this.matchLazy(
                (a, b) -> ((Pair<?, ?>) object).matchLazy(
                    (c, d) -> Objects.equals(a.get(), c.get()) && Objects.equals(b.get(), d.get())
                )
            );
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.match((first, second) -> (31 + first.hashCode()) * 31 + second.hashCode());
    }

    @Override
    public String toString() {
        return this.match((first, second) -> String.format("(%s, %s)", first, second));
    }

    /** The first element of this pair. */
    public T first() {
        return this.matchLazy((first, second) -> first.get());
    }

    /** The second element of this pair. */
    public U second(){
        return this.matchLazy((first, second) -> second.get());
    }

    /** Swap the elements of this pair. */
    public Pair<U, T> swap() {
        return Pair.lazy(() -> this.matchLazy(Functions.flip(Pair::of)));
    }

    /**
     * Lazily apply a function to the first element of this pair.
     *
     * @see Pair#mapSecond(Function)
     * @see Pair#mapBoth(Function,Function)
     * @see Pair#map(Pair,Function)
     */
    public <R> Pair<R, U> mapFirst(Function<? super T, ? extends R> function) {
        requireNonNull(function);
        return Pair.lazy(() -> this.matchLazy((x, y) -> Pair.of(() -> function.apply(x.get()), y)));
    }

    /**
     * Lazily apply a function to the second element of this pair.
     *
     * @see Pair#mapFirst(Function)
     * @see Pair#mapBoth(Function,Function)
     * @see Pair#map(Pair,Function)
     */
    public <R> Pair<T, R> mapSecond(Function<? super U, ? extends R> function) {
        requireNonNull(function);
        return Pair.lazy(() -> this.matchLazy((x, y) -> Pair.of(x, () -> function.apply(y.get()))));
    }

    /**
     * Lazily apply one function to the first element of this pair, and another function to the second element.
     *
     * @see Pair#mapFirst(Function)
     * @see Pair#mapSecond(Function)
     * @see Pair#map(Pair,Function)
     */
    public <R, S> Pair<R, S> mapBoth(Function<? super T, ? extends R> first, Function<? super U, ? extends S> second) {
        requireNonNull(first);
        requireNonNull(second);
        return Pair.lazy(
            () -> this.matchLazy(
                (x, y) -> Pair.of(
                    () -> first.apply(x.get()),
                    () -> second.apply(y.get())
                )
            )
        );
    }
}
