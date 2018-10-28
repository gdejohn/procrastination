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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FunctionsTest {
    @Test
    void fix() {
        Function<Integer, Integer> factorial = Functions.fix(f -> n -> n == 0 ? 1 : n * f.apply(n - 1));
        assertThat(factorial.apply(6)).isEqualTo(720);
    }

    @Test
    void memoizeThrowing() {
        var list = new LinkedList<String>();
        Supplier<String> first = list::getFirst;
        var memoized = Functions.memoize(first);
        var exception = catchThrowableOfType(memoized::get, NoSuchElementException.class);
        assertThatThrownBy(first::get).isNotSameAs(exception);
        assertThatThrownBy(memoized::get).isSameAs(exception);
        list.add("foo");
        assertThatThrownBy(memoized::get).isSameAs(exception);
    }

    @Test
    void memoizeNull() {
        var list = new LinkedList<String>();
        Supplier<String> first = list::getFirst;
        var memoized = Functions.memoize(first);
        list.add(null);
        var exception = catchThrowableOfType(memoized::get, NullPointerException.class);
        assertThatThrownBy(memoized::get).isSameAs(exception);
        list.set(0, "foo");
        assertThatThrownBy(memoized::get).isSameAs(exception);
    }

    @SuppressWarnings("unchecked")
    private static <T, X extends Exception> T sneak(Supplier<? extends Exception> exception) throws X {
        throw (X) exception.get();
    }

    @Test
    void memoizeSneakyThrowing() {
        Supplier<String> supplier = () -> sneak(IOException::new);
        var memoized = Functions.memoize(supplier);
        var exception = catchThrowableOfType(memoized::get, IOException.class);
        assertThatThrownBy(supplier::get).isNotSameAs(exception);
        assertThatThrownBy(memoized::get).isSameAs(exception);
    }
}
