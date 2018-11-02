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

package hu.akarnokd.rxjava2.interop;

import static org.junit.Assert.assertEquals;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Test;

import hu.akarnokd.rxjava2.interop.FlowableFromStream.StreamSubscription;
import io.reactivex.Flowable;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

public class FlowableFromStreamTest {

    @Test
    public void closedAtTheEnd() {
        AtomicInteger closed = new AtomicInteger();
        FlowableInterop.fromStream(IntStream.range(1, 6)
                .onClose(() -> closed.getAndIncrement())
                .boxed())
        .test()
        .assertResult(1, 2, 3, 4, 5);

        assertEquals(1, closed.get());
    }

    @Test
    public void empty() {
        AtomicInteger closed = new AtomicInteger();
        FlowableInterop.fromStream(IntStream.range(1, 1)
                .onClose(() -> closed.getAndIncrement())
                .boxed())
        .test()
        .assertResult();

        assertEquals(1, closed.get());
    }

    @Test
    public void closedInTheMiddle() {
        AtomicInteger closed = new AtomicInteger();

        FlowableInterop.fromStream(IntStream.range(1, 6)
                .onClose(() -> closed.getAndIncrement())
                .boxed())
        .take(3)
        .test()
        .assertResult(1, 2, 3);

        assertEquals(1, closed.get());
    }

    @Test
    public void noReuse() {
        Flowable<Integer> source = FlowableInterop.fromStream(Collections.singleton(1).stream());

        source.test().assertResult(1);

        source.test().assertFailure(IllegalStateException.class);
    }

    @Test
    public void iteratorHasNextCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        StreamSubscription<Integer> sd = new StreamSubscription<Integer>(ts,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        throw new IllegalArgumentException();
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        return 1;
                    }
                }
        );

        ts.onSubscribe(sd);

        ts.assertFailure(IllegalArgumentException.class, 1);

        assertEquals(1, calls.get());
    }

    @Test
    public void iteratorHasNextDisposeAfter() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        StreamSubscription<Integer> sd = new StreamSubscription<Integer>(ts,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        ts.dispose();
                        return true;
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        return 1;
                    }
                }
        );

        ts.onSubscribe(sd);

        ts.assertValuesOnly(1);

        assertEquals(1, calls.get());
    }

    @Test
    public void iteratorNextCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        StreamSubscription<Integer> sd = new StreamSubscription<Integer>(ts,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        return true;
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        throw new IllegalArgumentException();
                    }
                }
        );

        ts.onSubscribe(sd);

        ts.assertFailure(IllegalArgumentException.class);

        assertEquals(1, calls.get());
    }

    @Test
    public void iteratorNextDisposeAfter() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        StreamSubscription<Integer> sd = new StreamSubscription<Integer>(ts,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        return true;
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        ts.dispose();
                        return 1;
                    }
                }
        );

        ts.onSubscribe(sd);

        ts.assertValuesOnly(1);

        assertEquals(1, calls.get());
    }


    @Test
    public void closeCrash() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            TestSubscriber<Integer> ts = new TestSubscriber<>();

            StreamSubscription<Integer> sd = new StreamSubscription<Integer>(ts,
                    () -> { throw new IllegalArgumentException(); },
                    Collections.singleton(1).iterator());

            ts.onSubscribe(sd);

            ts.assertResult(1);

            TestHelper.assertError(errors, 0, IllegalArgumentException.class);
            assertEquals(1, errors.size());
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void backpressured() {
        TestSubscriber<Integer> ts = FlowableInterop.fromStream(IntStream.range(1, 6)
                .boxed())
        .test(0L);

        ts.assertEmpty();

        ts.request(1);

        ts.assertValuesOnly(1);

        ts.request(2);

        ts.assertValuesOnly(1, 2, 3);

        ts.request(2);

        ts.assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void sngleStep() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(1L) {
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                request(1);
            }
        };
        FlowableInterop.fromStream(IntStream.range(1, 6)
                .boxed())
        .subscribe(ts);

        ts.assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void badRequest() {
        TestHelper.assertBadRequestReported(FlowableInterop.fromStream(IntStream.range(1, 6)
                .boxed()));
    }

    @Test
    public void asyncRequests() {
        for (int i = 0; i < 1000; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>(0L);

            FlowableInterop.fromStream(IntStream.range(1, 10001).boxed())
            .subscribe(ts);

            Runnable r = () -> {
                for (int j = 0; j < 10000; j++) {
                    ts.request(1);
                }
            };

            TestHelper.race(r, r, Schedulers.single());

            ts.assertValueCount(10000)
            .assertNoErrors()
            .assertComplete();
        }
    }

    @Test
    public void cancelAndRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0L);

        AtomicInteger calls = new AtomicInteger();

        StreamSubscription<Integer> sd = new StreamSubscription<Integer>(ts,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        return true;
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        return 1;
                    }
                }
        );

        ts.onSubscribe(sd);

        sd.cancel();
        sd.request(1);
    }
}
