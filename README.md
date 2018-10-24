[![Artifact repository](https://img.shields.io/badge/jitpack-SNAPSHOT-blue.svg)][snapshot]
[![Javadoc](https://img.shields.io/badge/javadoc-SNAPSHOT-brightgreen.svg)][javadoc]
[![License](https://img.shields.io/github/license/gdejohn/procrastination.svg)][license]
[![Build status](https://travis-ci.com/gdejohn/procrastination.svg?branch=master)][ci]
[![Code coverage](https://img.shields.io/codecov/c/github/gdejohn/procrastination.svg)][coverage]

# procrastination

**procrastination** is a modular, zero-dependency library for Java 11 that provides:

* lazily evaluated, memoizing, purely functional data structures
* ad hoc pattern matching
* an extensible, reusable alternative to Java 8's [`Stream`][stream]
* stack-safe tail-recursive lambda expressions via trampolines and fixed points

## Data Structures

The included data structures are lazily evaluated. They procrastinate, doing the absolute minimum amount of work
required and putting it off for as long as possible, only computing each of their elements on demand. They can also be
memoized such that each element is computed at most once, the first time it's asked for, and then cached.

Because the data structures are purely functional, they are fully persistent. Instead of mutators, methods are provided
that return a new version of a given data structure reflecting the desired changes, leaving the previous version
intact. Every version remains accessible. This is implemented efficiently via structural sharing, which is safe because
the data structures are structurally immutable (i.e., elements cannot be added, removed, or replaced). 

The data structures are designed to emulate algebraic data types, with basic "data constructor" static factory methods
mirrored by abstract `match()` instance methods simulating pattern matching. This is not a general pattern-matching
facility; there is no matching against literals, no wildcard patterns, no nested patterns. It simply makes it possible
to distinguish which data constructor was used and extract the components in a single step.

The rest of the operations on these data structures are all ultimately defined in terms of the `match()` methods and
data constructors. None of the classes hide anything interesting. They don't declare any instance fields. They each
have just one constructor (in the Java sense), declared private, taking no arguments, with an empty body. If some
useful operation is missing, anyone can define it externally as a static method just as easily as writing an instance
method inside the class.

While it is easy to define new operations on these types, it is impossible to add new cases. Since the classes don't
expose their (Java) constructors, they are effectively sealed types, so the `match()` methods will always exhaustively
cover every case.

### Sequence

[`Sequence`][sequence] is an ordered collection of zero or more non-null elements (duplicates allowed). It is
recursively defined: a sequence is either [`empty`][empty], or it's [`constructed`][cons] from a head element and a
tail sequence. Conversely, the instance method [`Sequence.match(BiFunction,Supplier)`][match] pulls a sequence apart:
if the sequence is non-empty, it applies the given binary function to the head and tail and returns the result,
otherwise it returns a default value produced by the given supplier. Because sequences are lazy, it is perfectly
natural to work with infinite sequences. (Just be careful not to fully evaluate them!)

### Maybe

[`Maybe`][maybe] represents a value that may or may not exist. It can be thought of as a sequence with at most one
element and is often used to model potential failure. `Maybe` is a lazy alternative to [`Optional`][optional].

### Either

[`Either`][either] is a container with exactly one element that can take on one of two possible values, labeled *left*
and *right*, which may have different types. Like `Maybe`, it can be used to model failure, but it allows information
to be attached to the failure case (e.g., an exception, or a string error message). In that sense, it is the
data-structure analogue of Java's checked exceptions.

### Pair

[`Pair`][pair] is an ordered collection with exactly two elements which may have different types (i.e., a 2-tuple).
`Pair` is similar to [`Entry`][entry], but by contrast it is lazy and purely functional.

## Sequences vs. Streams

`Sequence` offers an alternative to the Stream API introduced in Java 8. Unlike streams, sequences can be traversed any
number of times (although this does mean that sequences derived from one-shot sources like iterators *must* be
memoized). Sequences also provide a much more comprehensive API, and it's significantly easier to define new
functionality for sequences. One of the biggest goals of the Stream API was parallel processing, which is why streams
were designed around [`spliterators`][spliterator]. So, processing a given stream in a way that isn't covered by the
API means working directly with its spliterator, and creating a new stream in a way that isn't covered by the API means
implementing a spliterator. Consider the instance method [`Sequence.scanLeft(Object,BiFunction)`][scan], which returns
the lazy sequence of intermediate results of a left fold. Here's a basic implementation for streams:

```java
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

static <T, R> Stream<R> scanLeft(Stream<T> stream, R initial, BiFunction<R, T, R> function) {
    return StreamSupport.stream(
        new AbstractSpliterator<>(Long.MAX_VALUE /* estimated size */, 0 /* characteristics */) {
            Spliterator<T> spliterator = null;

            R result = initial;

            @Override
            public boolean tryAdvance(Consumer<? super R> action) {
                if (spliterator == null) {
                    spliterator = stream.spliterator();
                } else if (!spliterator.tryAdvance(element -> result = function.apply(result, element))) {
                    return false;
                }
                action.accept(result);
                return true;
            }
        },
        false // parallel
    );
}
```

It's imperative, stateful, and the control flow is a little hard to follow; not exactly pleasant. (Note that the
characteristics and estimated size of the source stream's spliterator are ignored.) On the other hand, because
sequences are recursively defined, they admit a concise, natural recursive implementation of `scanLeft()` that anyone
could easily write if it weren't already included:

```java
import io.github.gdejohn.procrastination.Sequence;
import java.util.function.BiFunction;

static <T, R> Sequence<R> scanLeft(Sequence<T> sequence, R initial, BiFunction<R, T, R> function) {
    return Sequence.cons(
        initial,
        () -> sequence.match(
            (head, tail) -> scanLeft(tail, function.apply(initial, head), function),
            () -> Sequence.empty()
        )
    );
}
```

Compared to the stream implementation, it's declarative, straightforward, and a lot less code; not bad! (With a little
bit of code golfing, the method body could even fit comfortably on a single line!) The trade-off is that sequences are,
as the name suggests, sequential; there is no parallel processing. The priority here is developer ergonomics. If you
ever find yourself reaching for something in the Stream API that isn't there, and your workload doesn't benefit from
parallelism, give `Sequence` a try!

## Trampolines

Applying imperative idioms to sequences is ugly and error prone; recursive data types call for recursive algorithms.
Unfortunately, Java isn't very recursion friendly: deep call stacks quickly run afoul of stack overflow exceptions, and
tail recursion doesn't help because there's no tail-call elimination. This isn't a problem for lazy operations like
[`Sequence.map(Function)`][map], but whenever a potentially large number of elements must be eagerly traversed, as in
[`Sequence.filter(Predicate)`][filter], stack overflow is waiting to pounce. Enter trampolines.

[`Trampoline`][trampoline] transforms tail recursion into a stack-safe loop. To trampoline a tail-recursive method with
return type `R`, change the return type to `Trampoline<R>`, wrap the expressions returned in base cases with the static
factory method [`terminate()`][terminate], suspend recursive calls in [`Supplier`][supplier] lambda expressions, and
wrap the suspended recursive calls with the static factory method [`call()`][callSupplier]. For example:

```java
/** True if and only if the sequence contains at least one element that satisfies the predicate. */
static <T> Trampoline<Boolean> any(Sequence<T> sequence, Predicate<T> predicate) {
    return sequence.match(
        (head, tail) -> predicate.test(head) ? terminate(true) : call(() -> any(tail, predicate)),
        () -> terminate(false)
    );
}
```

To get the result from a trampoline, use the instance method [`evaluate()`][evaluate]:

```java
boolean result = any(sequence, predicate).evaluate();
```

### Anonymous Recursion

Tail recursion often requires additional "accumulator" parameters, and trampolining means that the result must be
unwrapped. These are irrelevant and burdensome implementation details that shouldn't be exposed to client code, so the
usual practice is to delegate to a private helper method. Alternatively, the recursive computation can be defined
inline!

[`Functions.fix()`][fix] returns the fixed point of a unary operator on functions, enabling recursive lambda
expressions (i.e., anonymous recursion). Lambda expressions by definition are unnamed, making explicit recursion
impossible. The trick here is to abstract the recursive call by taking the function itself as an argument and letting
`fix()` tie the knot. For example:

```java
Function<Integer, Integer> factorial = fix(f -> n -> n == 0 ? 1 : n * f.apply(n - 1));
```

This also works for trampolined functions and curried functions of arbitrarily many arguments.
[`Trampoline.evaluate()`][evaluateHelper] isn't just an instance method, it's also overloaded as an all-in-one static
helper method that accepts a trampolined recursive lambda expression and an appropriate number of arguments, fixes the
lambda expression, applies it to the arguments, evaluates the resulting trampoline, and returns the unwrapped value.
And to complement this approach, the static factory method [`Trampoline.call()`][callFunction] is overloaded to accept
curried functions and matching arguments. For example:

```java
static int length(Sequence<?> sequence) {
    return Trampoline.evaluate(
        sequence,
        0,
        f -> seq -> n -> seq.match(
            (head, tail) -> call(f, tail, n + 1),
            () -> terminate(n)
        )
    );
}
```

That's a stack-safe tail-recursive local helper function taking full advantage of type inference and pattern matching!
You might just forget that you're writing Java!

## Getting Started

### Gradle

Add JitPack to your root `build.gradle` at the end of the repositories:

```gradle
allprojects {
    repositories {
        // ...
        maven { url 'https://jitpack.io' }
    }
}
```

And add the dependency:

```gradle
dependencies {
    implementation 'io.github.gdejohn:procrastination:master-SNAPSHOT'
}
```

### Maven

Add the JitPack repository to your `pom.xml`:

```maven-pom
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

And add the dependency:

```maven-pom
<dependency>
    <groupId>io.github.gdejohn</groupId>
    <artifactId>procrastination</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

See instructions for other build tools at [JitPack][snapshot].

### jshell

The included jshell script [`procrastination.jsh`][script] makes it easy to play around with this library, assuming JDK
11 and a recent version of Maven are installed and present on your `PATH`. Just clone the repository, and from the root
directory run `mvn compile` and `jshell procrastination.jsh`. The script adds the module to the jshell environment and
imports all of the types and static members.

[callFunction]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Trampoline.html#call(java.util.function.Function,T,U)
[callSupplier]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Trampoline.html#call(java.util.function.Supplier)
[ci]: https://travis-ci.com/gdejohn/procrastination
[cons]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#cons(T,io.github.gdejohn.procrastination.Sequence)
[coverage]: https://codecov.io/gh/gdejohn/procrastination
[either]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Either.html
[empty]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#empty()
[entry]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Map.Entry.html
[evaluate]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Trampoline.html#evaluate()
[evaluateHelper]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Trampoline.html#evaluate(T,U,java.util.function.UnaryOperator)
[filter]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#filter(java.util.function.Predicate)
[fix]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Functions.html#fix(java.util.function.UnaryOperator)
[javadoc]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/
[license]: http://www.apache.org/licenses/LICENSE-2.0
[map]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#map(java.util.function.Function)
[match]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#match(java.util.function.BiFunction,java.util.function.Supplier)
[maybe]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Maybe.html
[memoize]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#memoize()
[optional]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Optional.html
[pair]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Pair.html
[scan]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html#scanLeft(R,java.util.function.BiFunction)
[script]: https://github.com/gdejohn/procrastination/blob/master/procrastination.jsh
[sequence]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Sequence.html
[snapshot]: https://jitpack.io/#io.github.gdejohn/procrastination/master-SNAPSHOT
[spliterator]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Spliterator.html
[stream]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/stream/Stream.html
[supplier]: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/function/Supplier.html
[terminate]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Trampoline.html#terminate(T)
[trampoline]: https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/io.github.gdejohn.procrastination/io/github/gdejohn/procrastination/Trampoline.html
