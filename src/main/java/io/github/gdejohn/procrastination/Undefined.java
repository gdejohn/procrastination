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
