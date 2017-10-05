# RxJava2Jdk8Interop


<a href='https://travis-ci.org/akarnokd/RxJava2Jdk8Interop/builds'><img src='https://travis-ci.org/akarnokd/RxJava2Jdk8Interop.svg?branch=master'></a>
[![codecov.io](http://codecov.io/github/akarnokd/RxJava2Jdk8Interop/coverage.svg?branch=master)](http://codecov.io/github/akarnokd/RxJava2Jdk8Interop?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.akarnokd/rxjava2-jdk8-interop/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.akarnokd/rxjava2-jdk8-interop)

RxJava 2.x: [![RxJava 2.x](https://maven-badges.herokuapp.com/maven-central/io.reactivex.rxjava2/rxjava/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.reactivex.rxjava2/rxjava)

RxJava 2 interop library for supporting Java 8 features such as Optional, Stream and CompletableFuture.

# Release

```groovy
compile 'com.github.akarnokd:rxjava2-jdk8-interop:0.2.5'
```

# Examples

The main entry points are:

  - `FlowableInterop`
  - `ObservableInterop`
  - `SingleInterop`
  - `MaybeInterop`
  - `CompletableInterop`

## Stream to RxJava

Note that `java.util.stream.Stream` can be consumed at most once and only
synchronously.

```java
Stream<T> stream = ...

Flowable<T> flow = FlowableInterop.fromStream(stream);

Observable<T> obs = ObservableInterop.fromStream(stream);
```

## Optional to RxJava

```java
Optional<T> opt = ...

Flowable<T> flow = FlowableInterop.fromOptional(opt);

Observable<T> obs = ObservableInterop.fromOptional(opt);
```

## CompletionStage to RxJava

Note that cancelling the Subscription won't cancel the `CompletionStage`.

```java
CompletionStage<T> cs = ...

Flowable<T> flow = FlowableInterop.fromFuture(cs);

Observable<T> flow = ObservableInterop.fromFuture(cs);
```

## Using Stream Collectors

```java
Flowable.range(1, 10)
.compose(FlowableInterop.collect(Collectors.toList()))
.test()
.assertResult(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
```

## Return the first/single/last element as a CompletionStage

```java
CompletionStage<Integer> cs = Flowable.just(1)
.delay(1, TimeUnit.SECONDS)
// return first
.to(FlowableInterop.first());

// return single
// .to(FlowableInterop.single());

// return last
// .to(FlowableInterop.last());

cs.whenComplete((v, e) -> {
   System.out.println(v);
   System.out.println(e);
});
```

## Return the first/last element optionally

This is a blocking operation

```java
Optional<Integer> opt = Flowable.just(1)
.to(FlowableInterop.firstElement());

System.out.println(opt.map(v -> v + 1).orElse(-1));
```

## Convert to Java Stream

This is a blocking operation. Closing the stream will cancel the RxJava sequence.

```java
Flowable.range(1, 10)
.to(FlowableInterop.toStream())
.parallel()
.map(v -> v + 1)
.forEach(System.out::println);
```

## FlatMap Java Streams

Note that since consuming a stream is practically blocking, there is no need
for a `maxConcurrency` parameter.

```java

Flowable.range(1, 5)
.compose(FlowableInterop.flatMapStream(v -> Arrays.asList(v, v + 1).stream()))
.test()
.assertResult(1, 2, 2, 3, 3, 4, 4, 5, 5, 6);
```

## Map based on Java Optional

```java
Flowable.range(1, 5)
.compose(FlowableInterop.mapOptional(v -> v % 2 == 0 ? Optional.of(v) : Optional.empty()))
.test()
.assertResult(2, 4);
```
