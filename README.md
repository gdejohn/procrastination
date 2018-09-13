[![JitPack repository](https://img.shields.io/jitpack/v/gdejohn/procrastination.svg?colorB=4EC820)](https://jitpack.io/#io.github.gdejohn/procrastination)
[![Javadoc](https://img.shields.io/badge/javadoc-SNAPSHOT-brightgreen.svg)](https://jitpack.io/io/github/gdejohn/procrastination/master-SNAPSHOT/javadoc/)
[![Build status](https://travis-ci.com/gdejohn/procrastination.svg?branch=master)](https://travis-ci.com/gdejohn/procrastination)

# procrastination

**procrastination** is a modular library for Java 10 that provides
* lazily evaluated, memoizing, persistent, purely functional data structures;
* and stack-safe tail-recursive lambda expressions via trampolines and fixed points of higher-order functions.

## Data Structures

### Sequence

`Sequence` is an ordered, homogeneous collection of zero or more non-null elements (duplicates allowed), implemented as
the classic functional singly-linked list. It is *recursively defined*: a sequence is either empty, or constructed from
a head element and a tail sequence. Conversely, the instance method `Sequence.match(BiFunction,Supplier)` pulls a
sequence apart, simulating *pattern matching*: if the sequence is non-empty, its head and tail are passed as arguments
to the given binary function to produce a result, otherwise the given supplier is invoked to produce a default result.

Sequences are *lazily evaluated*. They procrastinate, putting off work for as long as possible, only computing each
element on demand. They can also be *memoized* such that each element is computed at most once, the first time it is
asked for, and then cached. Because sequences are lazy, it is perfectly natural to work with infinite sequences. (Just
be careful not to fully evaluate them!)

Sequences are *fully persistent*. Instead of mutators, methods are provided that return a new version of a sequence
reflecting the desired changes, leaving the previous version intact. Every version of a sequence remains accessible.
This is achieved efficiently via *structural sharing*, which is safe because sequences are *structurally immutable*
(i.e., elements cannot be added, removed, or replaced). Because sequences never change, the longest common suffix of a
set of versions of a sequence can simply be referenced by all of those versions, rather than defensively copied over
and over.

### Maybe

`Maybe` represents a value that may or may not exist. It can be thought of as a sequence with at most one element and
is often used to model potential failure. `Maybe` is a lazy alternative to
[`Optional`](https://docs.oracle.com/javase/10/docs/api/java/util/Optional.html).

### Either

`Either` is a container with exactly one element that can take on one of two possible values, labeled *left* and
*right*, which may have different types. Like `Maybe`, it can be used to model failure, but it allows information to be
attached to the failure case (e.g., an exception, or a string error message).

### Pair

`Pair` is an ordered collection with exactly two elements which may have different types (i.e., a 2-tuple). `Pair` is a
lazy, purely functional alternative to [`Entry`](https://docs.oracle.com/javase/10/docs/api/java/util/Map.Entry.html).

## Trampolines and Fixed Points

Applying imperative idioms to sequences is ugly and error-prone; recursive data types call for recursive algorithms.
Unfortunately, Java isn't very recursion-friendly: deep call stacks quickly run afoul of stack overflow exceptions, and tail
recursion doesn't help because there's no tail-call elimination. Enter trampolines.

`Trampoline` converts tail recursion into a stack-safe loop. To trampoline a tail-recursive method, change the return
type `T` to `Trampoline<T>`, wrap the expressions returned in base cases with the static factory method
`Trampoline.terminate()`, suspend recursive calls in `Supplier` lambda expressions, and wrap the suspended recursive
calls with the static factory method `Trampoline.call()`. To get the result from a trampoline, invoke the instance
method `evaluate()`.

## Getting Started

### Maven

Add the JitPack repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

And add the dependency:

```xml
<dependency>
    <groupId>io.github.gdejohn</groupId>
    <artifactId>procrastination</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

### Gradle

Add JitPack to your root `build.gradle` at the end of the repositories:

```groovy
allprojects {
    repositories {
        // ...
        maven { url 'https://jitpack.io' }
    }
}
```

And add the dependency:

```groovy
dependencies {
    implementation 'io.github.gdejohn:procrastination:master-SNAPSHOT'
}
```

See instructions for other build tools at [JitPack](https://jitpack.io/#io.github.gdejohn/procrastination).

### jshell

The included jshell script `procrastination.jsh` makes it easy to play around with this library, assuming JDK 10 and a
recent version of Maven are installed and present on your `PATH`. Just clone the repository, and from the root directory
run `mvn compile` and `jshell procrastination.jsh`. The script sets up the jshell environment and imports all of the
types and static members.
