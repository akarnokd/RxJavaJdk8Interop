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

import java.util.Optional;

import io.reactivex.*;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Function;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.observers.BasicFuseableObserver;

/**
 * Maps an upstream value into an Optional and emits its value if not empty.
 *
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class ObservableMapOptional<T, R> extends Observable<R> {

    final ObservableSource<T> source;

    final Function<? super T, Optional<R>> mapper;

    ObservableMapOptional(ObservableSource<T> source, Function<? super T, Optional<R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(Observer<? super R> s) {
        source.subscribe(new MapOptionalObserver<>(s, mapper));
    }

    static final class MapOptionalObserver<T, R> extends BasicFuseableObserver<T, R> {

        final Function<? super T, Optional<R>> mapper;

        public MapOptionalObserver(Observer<? super R> actual, Function<? super T, Optional<R>> mapper) {
            super(actual);
            this.mapper = mapper;
        }

        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }

            if (sourceMode == ASYNC) {
                downstream.onNext(null);
                return;
            }

            Optional<R> o;

            try {
                o = ObjectHelper.requireNonNull(mapper.apply(t), "The mapper returned a null Optional");
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                fail(ex);
                return;
            }

            if (o.isPresent()) {
                downstream.onNext(o.get());
            }
        }

        @Override
        public int requestFusion(int mode) {
            return transitiveBoundaryFusion(mode);
        }

        @Override
        public R poll() throws Exception {
            for (;;) {
                T t = qd.poll();

                if (t == null) {
                    return null;
                }

                Optional<R> o = mapper.apply(t);

                if (o.isPresent()) {
                    return o.get();
                }
            }
        }
    }
}
