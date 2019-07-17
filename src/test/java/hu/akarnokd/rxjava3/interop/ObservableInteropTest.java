/*
 * Copyright 2016 David Karnok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.akarnokd.rxjava3.interop;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

import org.junit.*;

import hu.akarnokd.rxjava3.interop.ObservableInterop;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposables;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.UnicastSubject;

public class ObservableInteropTest {

    @Test
    public void utilityClass() {
        TestHelper.checkUtilityClass(ObservableInterop.class);
    }

    @Test
    public void fromStream() {
        ObservableInterop.fromStream(Arrays.asList(1, 2, 3, 4, 5).stream())
        .test()
        .assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void fromStream2() {
        Stream<Integer> s = Arrays.asList(1, 2, 3, 4, 5).stream();
        ObservableInterop.fromStream(s)
        .test()
        .assertResult(1, 2, 3, 4, 5);

        ObservableInterop.fromStream(s)
        .test()
        .assertFailure(IllegalStateException.class);
    }

    @Test
    public void fromOptional() {
        ObservableInterop.fromOptional(Optional.of(1))
        .test()
        .assertResult(1);

        ObservableInterop.fromOptional(Optional.empty())
        .test()
        .assertResult();
    }

    @Test
    public void fromFuture() {
        ObservableInterop.fromFuture(CompletableFuture.supplyAsync(() -> 1))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1);
    }

    @Test
    public void fromFutureError() {
        TestObserverEx<Object> ts = new TestObserverEx<>();
        
        ObservableInterop.fromFuture(CompletableFuture.supplyAsync(() -> { throw new IllegalArgumentException(); }))
        .subscribeWith(ts)
        .awaitDone(5, TimeUnit.SECONDS)
        .assertFailure(CompletionException.class);

        Throwable c = ts.errors().get(0).getCause();
        Assert.assertTrue(c.toString(), c instanceof IllegalArgumentException);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void collector() {
        Observable.range(1, 5)
        .compose(ObservableInterop.collect(Collectors.toList()))
        .test()
        .assertResult(Arrays.asList(1, 2, 3, 4, 5));
    }

    @Test
    public void first() {
        TestHelper.assertFuture(1, Observable.range(1, 5)
                .to(ObservableInterop.first())
        );
    }

    @Test(expected = NoSuchElementException.class)
    public void firstEmpty() {
        TestHelper.assertFuture(null, Observable.empty()
                .to(ObservableInterop.first())
        );
    }

    @Test
    public void last() {
        TestHelper.assertFuture(5, Observable.range(1, 5)
                .to(ObservableInterop.last())
        );
    }

    @Test(expected = NoSuchElementException.class)
    public void lastEmpty() {
        TestHelper.assertFuture(null, Observable.empty()
                .to(ObservableInterop.last())
        );
    }

    @Test
    public void single() {
        TestHelper.assertFuture(1, Observable.just(1)
                .to(ObservableInterop.single())
        );
    }

    @Test(expected = NoSuchElementException.class)
    public void singleEmpty() {
        TestHelper.assertFuture(null, Observable.empty()
                .to(ObservableInterop.single())
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void singleLonger() {
        TestHelper.assertFuture(null, Observable.range(1, 5)
                .to(ObservableInterop.single())
        );
    }

    @Test
    public void toStream() {
        List<Integer> list = Observable.just(1, 2, 3, 4, 5)
        .to(ObservableInterop.toStream())
        .collect(Collectors.toList());

        Assert.assertEquals(Arrays.asList(1, 2, 3, 4, 5), list);
    }

    @Test
    public void toStreamCancel() {
        UnicastSubject<Integer> up = UnicastSubject.create();

        up.onNext(1);
        up.onNext(2);
        up.onNext(3);
        up.onNext(4);
        up.onNext(5);

        try (Stream<Integer> s = up
                .to(ObservableInterop.toStream()).limit(3)) {
            Assert.assertTrue(up.hasObservers());

            List<Integer> list = s.collect(Collectors.toList());
            Assert.assertEquals(Arrays.asList(1, 2, 3), list);
        }

        Assert.assertFalse(up.hasObservers());
    }

    @Test
    public void firstElement() {
        Assert.assertEquals((Integer)1, Observable.range(1, 5)
        .to(ObservableInterop.firstElement()).get());
    }

    @Test
    public void firstElementEmpty() {
        Assert.assertFalse(Observable.empty()
        .to(ObservableInterop.firstElement()).isPresent());
    }

    @Test
    public void lastElement() {
        Assert.assertEquals((Integer)5, Observable.range(1, 5)
        .to(ObservableInterop.lastElement()).get());
    }

    @Test
    public void lastElementEmpty() {
        Assert.assertFalse(Observable.empty()
        .to(ObservableInterop.lastElement()).isPresent());
    }

    @Test
    public void flatMapStream() {
        Observable.range(1, 5)
        .compose(ObservableInterop.flatMapStream(v -> Arrays.asList(v, v + 1).stream()))
        .test()
        .assertResult(1, 2, 2, 3, 3, 4, 4, 5, 5, 6);
    }

    @Test
    public void mapOptional() {
        Observable.range(1, 5).hide()
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .test()
        .assertResult(-2, -4);
    }

    @Test
    public void mapOptionalError() {
        Observable.<Integer>error(new IOException())
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .test()
        .assertFailure(IOException.class);
    }

    @Test
    public void mapOptionalSyncFused() {
        TestObserverEx<Integer> ts = TestHelper.fusedObserver(QueueSubscription.ANY);

        Observable.range(1, 5)
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .subscribeWith(ts)
        .assertFusionMode(QueueSubscription.SYNC)
        .assertResult(-2, -4);
    }

    @Test
    public void mapOptionalAsyncFused() {
        TestObserverEx<Integer> ts = TestHelper.fusedObserver(QueueSubscription.ANY);

        UnicastSubject<Integer> up = UnicastSubject.create();
        TestHelper.emit(up, 1, 2, 3, 4, 5);

        up
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .subscribeWith(ts)
        .assertFusionMode(QueueSubscription.ASYNC)
        .assertResult(-2, -4);
    }

    @Test
    public void mapOptionalConditional() {
        Observable.range(1, 5).hide()
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .filter(Functions.alwaysTrue())
        .test()
        .assertResult(-2, -4);
    }

    @Test
    public void mapOptionalErrorConditional() {
        Observable.<Integer>error(new IOException())
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .filter(Functions.alwaysTrue())
        .test()
        .assertFailure(IOException.class);
    }

    @Test
    public void mapOptionalSyncFusedConditional() {
        TestObserverEx<Integer> ts = TestHelper.fusedObserver(QueueSubscription.ANY);

        Observable.range(1, 5)
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .filter(Functions.alwaysTrue())
        .subscribeWith(ts)
        .assertFusionMode(QueueSubscription.SYNC)
        .assertResult(-2, -4);
    }

    @Test
    public void mapOptionalAsyncFusedConditional() {
        TestObserverEx<Integer> ts = TestHelper.fusedObserver(QueueSubscription.ANY);

        UnicastSubject<Integer> up = UnicastSubject.create();
        TestHelper.emit(up, 1, 2, 3, 4, 5);

        up
        .compose(ObservableInterop.mapOptional(v -> {
            if (v % 2 == 0) {
                return Optional.of(-v);
            }
            return Optional.empty();
        }))
        .filter(Functions.alwaysTrue())
        .subscribeWith(ts)
        .assertFusionMode(QueueSubscription.ASYNC)
        .assertResult(-2, -4);
    }

    @Test
    public void mapOptionalMapperCrash() {
        Observable.just(1)
        .compose(ObservableInterop.mapOptional(v -> null))
        .test()
        .assertFailure(NullPointerException.class);
    }

    @Test
    public void mapOptionalCancelIgnored() {
        new Observable<Integer>() {
            @Override
            protected void subscribeActual(Observer<? super Integer> observer) {
                observer.onSubscribe(Disposables.empty());
                observer.onNext(1);
                observer.onNext(2);
            }
        }
        .compose(ObservableInterop.mapOptional(v -> { throw new IOException(); }))
        .test()
        .assertFailure(IOException.class);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void collectorInitCrash() {
        Observable.range(1, 5)
        .compose(ObservableInterop.collect(
        new Collector<Integer, Object, List<Integer>>() {

            @Override
            public Supplier<Object> supplier() {
                throw new IllegalArgumentException();
            }

            @Override
            public BiConsumer<Object, Integer> accumulator() {
                return (BiConsumer)Collectors.toList().accumulator();
            }

            @Override
            public BinaryOperator<Object> combiner() {
                return (BinaryOperator)Collectors.toList().combiner();
            }

            @Override
            public Function<Object, List<Integer>> finisher() {
                return (Function)Collectors.toList().finisher();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collectors.toList().characteristics();
            }
        }))
        .test()
        .assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void collectorDoubleOnSubscribe() {
        TestHelper.checkDoubleOnSubscribeObservable(o ->
            o.compose(ObservableInterop.collect(Collectors.toList()))
        );
    }

    @Test
    public void dispose() {
        TestHelper.checkDisposed(
                Observable.never()
                .compose(ObservableInterop.collect(Collectors.toList()))
        );
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void collectorAccumulatorCrash() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            new Observable<Integer>() {
                @Override
                protected void subscribeActual(Observer<? super Integer> observer) {
                    observer.onSubscribe(Disposables.empty());
                    observer.onNext(1);
                    observer.onNext(2);
                    observer.onComplete();
                    observer.onError(new IOException());
                }
            }
            .compose(ObservableInterop.collect(
            new Collector<Integer, Object, List<Integer>>() {

                @Override
                public Supplier<Object> supplier() {
                    return (Supplier)Collectors.toList().supplier();
                }

                @Override
                public BiConsumer<Object, Integer> accumulator() {
                    return (o, i) -> { throw new IllegalArgumentException(); };
                }

                @Override
                public BinaryOperator<Object> combiner() {
                    return (BinaryOperator)Collectors.toList().combiner();
                }

                @Override
                public Function<Object, List<Integer>> finisher() {
                    return (Function)Collectors.toList().finisher();
                }

                @Override
                public Set<Characteristics> characteristics() {
                    return Collectors.toList().characteristics();
                }
            }))
            .test()
            .assertFailure(IllegalArgumentException.class);

            TestHelper.assertUndeliverable(errors, 0, IOException.class);
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void collectorFinisherCrash() {
        new Observable<Integer>() {
            @Override
            protected void subscribeActual(Observer<? super Integer> observer) {
                observer.onSubscribe(Disposables.empty());
                observer.onNext(1);
                observer.onNext(2);
                observer.onComplete();
            }
        }
        .compose(ObservableInterop.collect(
        new Collector<Integer, Object, List<Integer>>() {

            @Override
            public Supplier<Object> supplier() {
                return (Supplier)Collectors.toList().supplier();
            }

            @Override
            public BiConsumer<Object, Integer> accumulator() {
                return (BiConsumer)Collectors.toList().accumulator();
            }

            @Override
            public BinaryOperator<Object> combiner() {
                return (BinaryOperator)Collectors.toList().combiner();
            }

            @Override
            public Function<Object, List<Integer>> finisher() {
                return o -> { throw new IllegalArgumentException(); };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collectors.toList().characteristics();
            }
        }))
        .test()
        .assertFailure(IllegalArgumentException.class);
    }
}
