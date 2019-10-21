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

import java.util.Iterator;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;

import org.reactivestreams.*;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.internal.functions.ObjectHelper;
import io.reactivex.rxjava3.internal.subscriptions.*;
import io.reactivex.rxjava3.internal.util.BackpressureHelper;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Consume a {@link Stream} and close it when the sequence is done
 * or gets disposed.
 * @since 0.3.4
 */
final class FlowableFromStream<T> extends Flowable<T> {

    final Stream<T> stream;

    FlowableFromStream(Stream<T> stream) {
        this.stream = stream;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        Iterator<T> iterator;
        boolean hasNext;
        try {
            iterator = stream.iterator();
            hasNext = iterator.hasNext();
            if (!hasNext) {
                stream.close();
            }
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            EmptySubscription.error(ex, s);
            return;
        }
        if (!hasNext) {
            EmptySubscription.complete(s);
            return;
        }
        s.onSubscribe(new StreamSubscription<>(s, stream, iterator));
    }

    static final class StreamSubscription<T> extends AtomicInteger implements Subscription {

        private static final long serialVersionUID = 497982641532135424L;

        final Subscriber<? super T> downstream;

        AutoCloseable stream;

        Iterator<T> iterator;

        final AtomicLong requested;

        StreamSubscription(Subscriber<? super T> downstream, AutoCloseable stream, Iterator<T> iterator) {
            this.downstream = downstream;
            this.stream = stream;
            this.iterator = iterator;
            this.requested = new AtomicLong();
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                if (BackpressureHelper.add(requested, n) == 0) {
                    run(n);
                }
            }
        }

        void run(long requested) {
            Iterator<T> iterator = this.iterator;

            long emitted = 0L;
            for (;;) {

                if (getAndIncrement() == 0) {
                    T next;

                    try {
                        next = ObjectHelper.requireNonNull(iterator.next(), "The Iterator.next returned a null value");
                    } catch (Throwable ex) {
                        Exceptions.throwIfFatal(ex);
                        close();
                        downstream.onError(ex);
                        return;
                    }

                    downstream.onNext(next);
                    emitted++;

                    if (get() != 1) {
                        close();
                        return;
                    }
                } else {
                    return;
                }

                boolean hasNext;

                try {
                    hasNext = iterator.hasNext();
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    close();
                    downstream.onError(ex);
                    return;
                }

                if (decrementAndGet() != 0) {
                    close();
                    return;
                }

                if (!hasNext) {
                    close();
                    downstream.onComplete();
                    return;
                }

                if (emitted == requested) {

                    requested = this.requested.get();

                    if (emitted == requested) {
                        if (this.requested.compareAndSet(requested, 0)) {
                            return;
                        }
                        emitted = 0L;
                        requested = this.requested.get();
                    }
                }
            }
        }

        void close() {
            AutoCloseable ac = stream;
            stream = null;
            iterator = null;
            try {
                ac.close();
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                RxJavaPlugins.onError(ex);
            }
        }

        @Override
        public void cancel() {
            if (getAndIncrement() == 0) {
                close();
            }
        }
    }
}
