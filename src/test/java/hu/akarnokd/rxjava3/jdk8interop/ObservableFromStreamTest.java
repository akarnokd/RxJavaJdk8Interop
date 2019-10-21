/*
 * Copyright 2019 David Karnok
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

package hu.akarnokd.rxjava3.jdk8interop;

import static org.junit.Assert.assertEquals;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Test;

import hu.akarnokd.rxjava3.jdk8interop.ObservableInterop;
import hu.akarnokd.rxjava3.jdk8interop.ObservableFromStream.StreamDisposable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

public class ObservableFromStreamTest {

    @Test
    public void closedAtTheEnd() {
        AtomicInteger closed = new AtomicInteger();
        ObservableInterop.fromStream(IntStream.range(1, 6)
                .onClose(() -> closed.getAndIncrement())
                .boxed())
        .test()
        .assertResult(1, 2, 3, 4, 5);

        assertEquals(1, closed.get());
    }

    @Test
    public void closedInTheMiddle() {
        AtomicInteger closed = new AtomicInteger();

        ObservableInterop.fromStream(IntStream.range(1, 6)
                .onClose(() -> closed.getAndIncrement())
                .boxed())
        .take(3)
        .test()
        .assertResult(1, 2, 3);

        assertEquals(1, closed.get());
    }

    @Test
    public void noReuse() {
        Observable<Integer> source = ObservableInterop.fromStream(Collections.singleton(1).stream());

        source.test().assertResult(1);

        source.test().assertFailure(IllegalStateException.class);
    }

    @Test
    public void disposed() {
        TestHelper.checkDisposed(ObservableInterop.fromStream(Collections.singleton(1).stream()));
    }

    @Test
    public void iteratorHasNextCrash() {
        TestObserver<Integer> to = new TestObserver<>();

        AtomicInteger calls = new AtomicInteger();

        StreamDisposable<Integer> sd = new StreamDisposable<>(to,
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

        to.onSubscribe(sd);

        sd.run();

        to.assertFailure(IllegalArgumentException.class);

        assertEquals(0, calls.get());
    }

    @Test
    public void iteratorHasNextDisposeAfter() {
        TestObserver<Integer> to = new TestObserver<>();

        AtomicInteger calls = new AtomicInteger();

        StreamDisposable<Integer> sd = new StreamDisposable<>(to,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        to.dispose();
                        return true;
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        return 1;
                    }
                }
        );

        to.onSubscribe(sd);

        sd.run();

        to.assertEmpty();

        assertEquals(0, calls.get());
    }

    @Test
    public void iteratorNextCrash() {
        TestObserver<Integer> to = new TestObserver<>();

        AtomicInteger calls = new AtomicInteger();

        StreamDisposable<Integer> sd = new StreamDisposable<>(to,
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

        to.onSubscribe(sd);

        sd.run();

        to.assertFailure(IllegalArgumentException.class);

        assertEquals(1, calls.get());
    }

    @Test
    public void iteratorNextDisposeAfter() {
        TestObserver<Integer> to = new TestObserver<>();

        AtomicInteger calls = new AtomicInteger();

        StreamDisposable<Integer> sd = new StreamDisposable<>(to,
                () -> { },
                new Iterator<Integer>() {

                    @Override
                    public boolean hasNext() {
                        return true;
                    }

                    @Override
                    public Integer next() {
                        calls.getAndIncrement();
                        to.dispose();
                        return 1;
                    }
                }
        );

        to.onSubscribe(sd);

        sd.run();

        to.assertEmpty();

        assertEquals(1, calls.get());
    }


    @Test
    public void closeCrash() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            TestObserver<Integer> to = new TestObserver<>();

            StreamDisposable<Integer> sd = new StreamDisposable<>(to,
                    () -> { throw new IllegalArgumentException(); },
                    Collections.emptyIterator());

            to.onSubscribe(sd);

            sd.run();

            to.assertResult();

            TestHelper.assertError(errors, 0, IllegalArgumentException.class);
            assertEquals(1, errors.size());
        } finally {
            RxJavaPlugins.reset();
        }
    }
}
