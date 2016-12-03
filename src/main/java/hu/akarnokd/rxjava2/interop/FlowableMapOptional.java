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

import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

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
        // TODO Auto-generated method stub
        
    }
}
