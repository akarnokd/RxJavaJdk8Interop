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

package hu.akarnokd.rxjava3.jdk8interop;

import java.util.function.*;
import java.util.stream.Collector;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.internal.disposables.*;
import io.reactivex.rxjava3.internal.observers.DeferredScalarDisposable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Collect elements of the upstream with the help of the Collector's callback functions.
 *
 * @param <T> the upstream value type
 * @param <A> the accumulated type
 * @param <R> the result type
 */
final class ObservableCollector<T, A, R> extends Observable<R> {

    final ObservableSource<T> source;

    final Collector<T, A, R> collector;

    ObservableCollector(ObservableSource<T> source, Collector<T, A, R> collector) {
        this.source = source;
        this.collector = collector;
    }

    @Override
    protected void subscribeActual(Observer<? super R> s) {
        A initialValue;
        BiConsumer<A, T> accumulator;
        Function<A, R> finisher;

        try {
            initialValue = collector.supplier().get();

            accumulator = collector.accumulator();

            finisher = collector.finisher();
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            EmptyDisposable.error(ex, s);
            return;
        }

        source.subscribe(new CollectorObserver<>(s, initialValue, accumulator, finisher));
    }

    static final class CollectorObserver<T, A, R> extends DeferredScalarDisposable<R>
    implements Observer<T> {

        private static final long serialVersionUID = 2129956429647866524L;

        final BiConsumer<A, T> accumulator;

        final Function<A, R> finisher;

        A intermediate;

        Disposable upstream;

        boolean done;

        public CollectorObserver(Observer<? super R> actual,
                A initialValue, BiConsumer<A, T> accumulator, Function<A, R> finisher) {
            super(actual);
            this.intermediate = initialValue;
            this.accumulator = accumulator;
            this.finisher = finisher;
        }

        @Override
        public void onSubscribe(Disposable d) {
            if (DisposableHelper.validate(this.upstream, d)) {
                this.upstream = d;

                downstream.onSubscribe(this);
            }
        }

        @Override
        public void onNext(T t) {
            if (!done) {
                try {
                    accumulator.accept(intermediate, t);
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    upstream.dispose();
                    onError(ex);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
            } else {
                done = true;
                intermediate = null;
                downstream.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                R r;

                try {
                    r = finisher.apply(intermediate);
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    onError(ex);
                    return;
                }

                intermediate = null;
                complete(r);
            }
        }

        @Override
        public void dispose() {
            super.dispose();
            upstream.dispose();
        }
    }
}
