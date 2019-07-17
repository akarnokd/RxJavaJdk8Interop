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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Consume a {@link Stream} and close it when the sequence is done
 * or gets disposed.
 * @since 0.3.4
 */
final class ObservableFromStream<T> extends Observable<T> {

    final Stream<T> stream;

    ObservableFromStream(Stream<T> stream) {
        this.stream = stream;
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        Iterator<T> iterator;
        try {
            iterator = stream.iterator();
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            EmptyDisposable.error(ex, observer);
            return;
        }
        StreamDisposable<T> d = new StreamDisposable<T>(observer, stream, iterator);
        observer.onSubscribe(d);
        d.run();
    }

    static final class StreamDisposable<T>
    extends AtomicInteger
    implements Disposable {

        private static final long serialVersionUID = -7262727127695950226L;

        final Observer<? super T> downstream;

        AutoCloseable stream;

        volatile Iterator<T> iterator;

        StreamDisposable(Observer<? super T> downstream,
                AutoCloseable stream, Iterator<T> iterator) {
            this.downstream = downstream;
            this.stream = stream;
            this.iterator = iterator;
        }

        void run() {
            Iterator<T> iterator = this.iterator;

            for (;;) {

                if (getAndIncrement() == 0) {
                    boolean hasNext;

                    try {
                        hasNext = iterator.hasNext();
                    } catch (Throwable ex) {
                        Exceptions.throwIfFatal(ex);
                        close();
                        downstream.onError(ex);
                        break;
                    }

                    if (!hasNext) {
                        close();
                        downstream.onComplete();
                        break;
                    }

                    if (get() != 1) {
                        close();
                        break;
                    }
                } else {
                    break;
                }


                T next;

                try {
                    next = ObjectHelper.requireNonNull(iterator.next(), "The Iterator.next returned a null value");
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    close();
                    downstream.onError(ex);
                    break;
                }
                if (decrementAndGet() != 0) {
                    close();
                    break;
                }

                downstream.onNext(next);
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
        public void dispose() {
            if (getAndIncrement() == 0) {
                close();
            }
        }

        @Override
        public boolean isDisposed() {
            return iterator == null;
        }
    }
}
