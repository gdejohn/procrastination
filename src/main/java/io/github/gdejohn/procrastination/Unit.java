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

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The type with just one instance: the unit value.
 *
 * <p>The unit type is also known as {@code Null}, {@code Nil}, {@code None}, {@code Void}, or {@code ()} in other
 * languages. It carries no information and can be thought of as the type of a container that is always empty, or as
 * the empty product type (i.e., the product of zero types, whereas {@link Pair} is the product of two types).
 *
 * <p>A {@link Function} that returns {@code Unit} is analogous to a {@link Consumer}, or a {@code void} method (i.e.,
 * it does not return anything useful).
 *
 * <p>The uninstantiable class {@link Void} is sometimes used as a makeshift unit type by treating {@code null} as its
 * sole value. {@code Unit} offers an alternative to that pattern for codebases that prefer to deal with nulls at the
 * boundary and prohibit them internally.
 *
 * <p>Compare with {@link Boolean}, which has two values: {@link Boolean#TRUE TRUE} and {@link Boolean#FALSE FALSE}.
 *
 * @see Unit#unit()
 */
public final class Unit {
    private Unit() {}

    private static final Unit UNIT = new Unit();

    /**
     * The unit value (the only instance of the {@code Unit} type).
     *
     * <p>The unit value can be thought of as the empty sequence (0-tuple), or the empty set, or any empty container.
     *
     * @see Sequence#empty()
     * @see Maybe#empty()
     */
    public static Unit unit() {
        return UNIT;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Unit;
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "()";
    }
}
