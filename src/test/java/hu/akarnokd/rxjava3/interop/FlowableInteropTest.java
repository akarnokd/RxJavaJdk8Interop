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
import org.reactivestreams.Subscriber;

import io.reactivex.Flowable;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.subscriptions.BooleanSubscription;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.processors.UnicastProcessor;

public class FlowableInteropTest {

    @Test
    public void utilityClass() {
        TestHelper.checkUtilityClass(FlowableInterop.class);
    }

    @Test
    public void fromStream() {
        FlowableInterop.fromStream(Arrays.asList(1, 2, 3, 4, 5).stream())
        .test()
        .assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void fromStream2() {
        Stream<Integer> s = Arrays.asList(1, 2, 3, 4, 5).stream();
        FlowableInterop.fromStream(s)
        .test()
        .assertResult(1, 2, 3, 4, 5);

        FlowableInterop.fromStream(s)
        .test()
        .assertFailure(IllegalStateException.class);
    }

    @Test
    public void fromOptional() {
        FlowableInterop.fromOptional(Optional.of(1))
        .test()
        .assertResult(1);

        FlowableInterop.fromOptional(Optional.empty())
        .test()
        .assertResult();
    }

    @Test
    public void fromFuture() {
        FlowableInterop.fromFuture(CompletableFuture.supplyAsync(() -> 1))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1);
    }

    @Test
    public void fromFutureError() {
        TestSubscriberEx<Object> ts = FlowableInterop.fromFuture(
                CompletableFuture.supplyAsync(() -> { throw new IllegalArgumentException(); }))
        .subscribeWith(new TestSubscriberEx<>())
        .awaitDone(5, TimeUnit.SECONDS)
        .assertFailure(CompletionException.class);

        Throwable c = ts.errors().get(0).getCause();
        Assert.assertTrue(c.toString(), c instanceof IllegalArgumentException);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void collector() {
        Flowable.range(1, 5)
        .compose(FlowableInterop.collect(Collectors.toList()))
        .test()
        .assertResult(Arrays.asList(1, 2, 3, 4, 5));
    }

    @Test
    public void first() {
        TestHelper.assertFuture(1, Flowable.range(1, 5)
                .to(FlowableInterop.first())
        );
    }

    @Test(expected = NoSuchElementException.class)
    public void firstEmpty() {
        TestHelper.assertFuture(null, Flowable.empty()
                .to(FlowableInterop.first())
        );
    }

    @Test
    public void last() {
        TestHelper.assertFuture(5, Flowable.range(1, 5)
                .to(FlowableInterop.last())
        );
    }

    @Test(expected = NoSuchElementException.class)
    public void lastEmpty() {
        TestHelper.assertFuture(null, Flowable.empty()
                .to(FlowableInterop.last())
        );
    }

    @Test
    public void single() {
        TestHelper.assertFuture(1, Flowable.just(1)
                .to(FlowableInterop.single())
        );
    }

    @Test(expected = NoSuchElementException.class)
    public void singleEmpty() {
        TestHelper.assertFuture(null, Flowable.empty()
                .to(FlowableInterop.single())
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void singleLonger() {
        TestHelper.assertFuture(null, Flowable.range(1, 5)
                .to(FlowableInterop.single())
        );
    }

    @Test
    public void toStream() {
        List<Integer> list = Flowable.just(1, 2, 3, 4, 5)
        .to(FlowableInterop.toStream())
        .collect(Collectors.toList());

        Assert.assertEquals(Arrays.asList(1, 2, 3, 4, 5), list);
    }

    @Test
    public void toStreamCancel() {
        UnicastProcessor<Integer> up = UnicastProcessor.create();

        up.onNext(1);
        up.onNext(2);
        up.onNext(3);
        up.onNext(4);
        up.onNext(5);

        try (Stream<Integer> s = up
                .to(FlowableInterop.toStream()).limit(3)) {
            Assert.assertTrue(up.hasSubscribers());

            List<Integer> list = s.collect(Collectors.toList());
            Assert.assertEquals(Arrays.asList(1, 2, 3), list);
        }

        Assert.assertFalse(up.hasSubscribers());
    }

    @Test
    public void firstElement() {
        Assert.assertEquals((Integer)1, Flowable.range(1, 5)
        .to(FlowableInterop.firstElement()).get());
    }

    @Test
    public void firstElementEmpty() {
        Assert.assertFalse(Flowable.empty()
        .to(FlowableInterop.firstElement()).isPresent());
    }

    @Test
    public void lastElement() {
        Assert.assertEquals((Integer)5, Flowable.range(1, 5)
        .to(FlowableInterop.lastElement()).get());
    }

    @Test
    public void lastElementEmpty() {
        Assert.assertFalse(Flowable.empty()
        .to(FlowableInterop.lastElement()).isPresent());
    }

    @Test
    public void flatMapStream() {
        Flowable.range(1, 5)
        .compose(FlowableInterop.flatMapStream(v -> Arrays.asList(v, v + 1).stream()))
        .test()
        .assertResult(1, 2, 2, 3, 3, 4, 4, 5, 5, 6);
    }

    @Test
    public void mapOptional() {
        Flowable.range(1, 5).hide()
        .compose(FlowableInterop.mapOptional(v -> {
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
        Flowable.<Integer>error(new IOException())
        .compose(FlowableInterop.mapOptional(v -> {
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
        TestSubscriberEx<Integer> ts = TestHelper.fusedSubscriber(QueueSubscription.ANY);

        Flowable.range(1, 5)
        .compose(FlowableInterop.mapOptional(v -> {
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
        TestSubscriberEx<Integer> ts = TestHelper.fusedSubscriber(QueueSubscription.ANY);

        UnicastProcessor<Integer> up = UnicastProcessor.create();
        TestHelper.emit(up, 1, 2, 3, 4, 5);

        up
        .compose(FlowableInterop.mapOptional(v -> {
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
        Flowable.range(1, 5).hide()
        .compose(FlowableInterop.mapOptional(v -> {
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
        Flowable.<Integer>error(new IOException())
        .compose(FlowableInterop.mapOptional(v -> {
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
        TestSubscriberEx<Integer> ts = TestHelper.fusedSubscriber(QueueSubscription.ANY);

        Flowable.range(1, 5)
        .compose(FlowableInterop.mapOptional(v -> {
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
        TestSubscriberEx<Integer> ts = TestHelper.fusedSubscriber(QueueSubscription.ANY);

        UnicastProcessor<Integer> up = UnicastProcessor.create();
        TestHelper.emit(up, 1, 2, 3, 4, 5);

        up
        .compose(FlowableInterop.mapOptional(v -> {
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
        Flowable.just(1)
        .compose(FlowableInterop.mapOptional(v -> null))
        .test()
        .assertFailure(NullPointerException.class);
    }

    @Test
    public void mapOptionalMapperCrashConditional() {
        Flowable.just(1)
        .compose(FlowableInterop.mapOptional(v -> null))
        .filter(v -> true)
        .test()
        .assertFailure(NullPointerException.class);
    }

    @Test
    public void mapOptionalCancelIgnoredConditional() {
        new Flowable<Integer>() {
            @Override
            protected void subscribeActual(Subscriber<? super Integer> observer) {
                observer.onSubscribe(new BooleanSubscription());
                observer.onNext(1);
                observer.onNext(2);
            }
        }
        .compose(FlowableInterop.mapOptional(v -> { throw new IOException(); }))
        .filter(v -> true)
        .test()
        .assertFailure(IOException.class);
    }

    @Test
    public void mapOptionalCancelIgnored() {
        new Flowable<Integer>() {
            @Override
            protected void subscribeActual(Subscriber<? super Integer> observer) {
                observer.onSubscribe(new BooleanSubscription());
                observer.onNext(1);
                observer.onNext(2);
            }
        }
        .compose(FlowableInterop.mapOptional(v -> { throw new IOException(); }))
        .test()
        .assertFailure(IOException.class);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void collectorInitCrash() {
        Flowable.range(1, 5)
        .compose(FlowableInterop.collect(
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
        TestHelper.checkDoubleOnSubscribeFlowable(o ->
            o.compose(FlowableInterop.collect(Collectors.toList()))
        );
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void collectorAccumulatorCrash() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            new Flowable<Integer>() {
                @Override
                protected void subscribeActual(Subscriber<? super Integer> observer) {
                    observer.onSubscribe(new BooleanSubscription());
                    observer.onNext(1);
                    observer.onNext(2);
                    observer.onComplete();
                    observer.onError(new IOException());
                }
            }
            .compose(FlowableInterop.collect(
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
        new Flowable<Integer>() {
            @Override
            protected void subscribeActual(Subscriber<? super Integer> observer) {
                observer.onSubscribe(new BooleanSubscription());
                observer.onNext(1);
                observer.onNext(2);
                observer.onComplete();
            }
        }
        .compose(FlowableInterop.collect(
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

    @Test
    public void collectCancel() {
        boolean[] cancelled = { false };
        Flowable.never()
        .doOnCancel(() -> cancelled[0] = true)
        .compose(FlowableInterop.collect(Collectors.toList()))
        .test()
        .cancel();

        Assert.assertTrue(cancelled[0]);
    }
}
