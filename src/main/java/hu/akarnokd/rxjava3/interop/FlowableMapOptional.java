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

import java.util.Optional;

import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Function;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.subscribers.*;

/**
 * Maps an upstream value into an Optional and emits its value if not empty.
 *
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class FlowableMapOptional<T, R> extends Flowable<R> {

    final Publisher<T> source;

    final Function<? super T, Optional<R>> mapper;

    FlowableMapOptional(Publisher<T> source, Function<? super T, Optional<R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(Subscriber<? super R> s) {
        if (s instanceof ConditionalSubscriber) {
            source.subscribe(new MapOptionalConditionalSubscriber<>((ConditionalSubscriber<? super R>)s, mapper));
        } else {
            source.subscribe(new MapOptionalSubscriber<>(s, mapper));
        }
    }

    static final class MapOptionalSubscriber<T, R> extends BasicFuseableSubscriber<T, R>
    implements ConditionalSubscriber<T> {

        final Function<? super T, Optional<R>> mapper;

        public MapOptionalSubscriber(Subscriber<? super R> actual, Function<? super T, Optional<R>> mapper) {
            super(actual);
            this.mapper = mapper;
        }

        @Override
        public void onNext(T t) {
            if (!tryOnNext(t)) {
                upstream.request(1);
            }
        }

        @Override
        public boolean tryOnNext(T t) {
            if (done) {
                return false;
            }

            if (sourceMode == ASYNC) {
                downstream.onNext(null);
                return true;
            }

            Optional<R> o;

            try {
                o = ObjectHelper.requireNonNull(mapper.apply(t), "The mapper returned a null Optional");
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                fail(ex);
                return false;
            }

            if (o.isPresent()) {
                downstream.onNext(o.get());
                return true;
            }
            return false;
        }

        @Override
        public int requestFusion(int mode) {
            return transitiveBoundaryFusion(mode);
        }

        @Override
        public R poll() throws Throwable {
            for (;;) {
                T t = qs.poll();

                if (t == null) {
                    return null;
                }

                Optional<R> o = mapper.apply(t);

                if (o.isPresent()) {
                    return o.get();
                }

                if (sourceMode != SYNC) {
                    upstream.request(1);
                }
            }
        }
    }

    static final class MapOptionalConditionalSubscriber<T, R> extends BasicFuseableConditionalSubscriber<T, R>
    implements ConditionalSubscriber<T> {

        final Function<? super T, Optional<R>> mapper;

        public MapOptionalConditionalSubscriber(ConditionalSubscriber<? super R> actual, Function<? super T, Optional<R>> mapper) {
            super(actual);
            this.mapper = mapper;
        }

        @Override
        public void onNext(T t) {
            if (!tryOnNext(t)) {
                upstream.request(1);
            }
        }

        @Override
        public boolean tryOnNext(T t) {
            if (done) {
                return false;
            }

            if (sourceMode == ASYNC) {
                return downstream.tryOnNext(null);
            }

            Optional<R> o;

            try {
                o = ObjectHelper.requireNonNull(mapper.apply(t), "The mapper returned a null Optional");
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                fail(ex);
                return false;
            }

            if (o.isPresent()) {
                return downstream.tryOnNext(o.get());
            }
            return false;
        }

        @Override
        public int requestFusion(int mode) {
            return transitiveBoundaryFusion(mode);
        }

        @Override
        public R poll() throws Throwable {
            for (;;) {
                T t = qs.poll();

                if (t == null) {
                    return null;
                }

                Optional<R> o = mapper.apply(t);

                if (o.isPresent()) {
                    return o.get();
                }

                if (sourceMode != SYNC) {
                    upstream.request(1);
                }
            }
        }
    }
}
