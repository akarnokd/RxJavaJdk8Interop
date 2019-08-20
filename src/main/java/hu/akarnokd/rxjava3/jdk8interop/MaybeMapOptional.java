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

import java.util.Optional;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.internal.disposables.DisposableHelper;
import io.reactivex.rxjava3.internal.functions.ObjectHelper;

/**
 * Maps the success value of the source Maybe into an Optional and emits this Optional's value if present,
 * otherwise terminates.
 *
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class MaybeMapOptional<T, R> extends Maybe<R> {

    final MaybeSource<T> source;

    final Function<? super T, Optional<R>> mapper;

    public MaybeMapOptional(MaybeSource<T> source, Function<? super T, Optional<R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(MaybeObserver<? super R> observer) {
        source.subscribe(new MapOptionalObserver<>(observer, mapper));
    }

    static final class MapOptionalObserver<T, R> implements MaybeObserver<T>, Disposable {

        final MaybeObserver<? super R> actual;

        final Function<? super T, Optional<R>> mapper;

        Disposable d;

        public MapOptionalObserver(MaybeObserver<? super R> actual, Function<? super T, Optional<R>> mapper) {
            super();
            this.actual = actual;
            this.mapper = mapper;
        }

        @Override
        public void dispose() {
            d.dispose();
        }

        @Override
        public boolean isDisposed() {
            return d.isDisposed();
        }

        @Override
        public void onSubscribe(Disposable d) {
            if (DisposableHelper.validate(this.d, d)) {
                this.d = d;

                actual.onSubscribe(this);
            }
        }

        @Override
        public void onSuccess(T value) {
            Optional<R> v;

            try {
                v = ObjectHelper.requireNonNull(mapper.apply(value), "The mapper returned a null Optional");
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                actual.onError(ex);
                return;
            }

            if (v.isPresent()) {
                actual.onSuccess(v.get());
            } else {
                actual.onComplete();
            }
        }

        @Override
        public void onError(Throwable e) {
            actual.onError(e);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }

    }
}
