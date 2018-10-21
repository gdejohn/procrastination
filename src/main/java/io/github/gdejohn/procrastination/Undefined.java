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

import java.util.function.Supplier;

final class Undefined extends Error {
    private Undefined() {
        super(null, null, false, false); // no message, no cause, suppression disabled, no stack trace
    }

    private static final Undefined INSTANCE = new Undefined();

    private static final Supplier<?> UNDEFINED = () -> {
        throw INSTANCE;
    };

    static <T> Supplier<T> undefined() {
        @SuppressWarnings("unchecked") // this cast is safe because the supplier always throws
        var undefined = (Supplier<T>) UNDEFINED;
        return undefined;
    }
}
