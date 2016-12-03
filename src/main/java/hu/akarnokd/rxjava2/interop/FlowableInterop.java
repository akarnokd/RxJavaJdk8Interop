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

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.*;

import io.reactivex.*;
import io.reactivex.functions.Function;

/**
 * Utility methods, sources and operators supporting RxJava 2 and the Jdk 8 API
 * interoperation.
 * 
 * @since 0.1.0
 */
public final class FlowableInterop {

    /** Utility class. */
    private FlowableInterop() {
        throw new IllegalStateException("No instances!");
    }

    public static <T> Flowable<T> flowableFromStream(Stream<T> stream) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Flowable<T> flowableFromOptional(Optional<T> opt) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Flowable<T> flowableFromFuture(CompletionStage<T> cs) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T, A, R> FlowableTransformer<T, R> flowableCollect(Collector<T, A, R> collector) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Function<Flowable<T>, CompletionStage<T>> flowableFirst() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Function<Flowable<T>, CompletionStage<T>> flowableSingle() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Function<Flowable<T>, CompletionStage<T>> flowableLast() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Function<Flowable<T>, Stream<T>> flowableToStream() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Block until the source Flowable emits its first item and return that as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Flowable.to()}.
     */
    public static <T> Function<Flowable<T>, Optional<T>> flowableFirstElement() {
        return f -> Optional.ofNullable(f.blockingFirst(null));
    }

    /**
     * Block until the source Flowable completes and return its last value as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Flowable.to()}.
     */
    public static <T> Function<Flowable<T>, Optional<T>> flowableLastElement() {
        return f -> Optional.ofNullable(f.blockingLast(null));
    }

    /**
     * Map each value of the upstream into a Stream and flatten them into a single sequence.
     * @param <T> the input value type
     * @param <R> the Stream type
     * @param mapper the function that returns a Stream for each upstream value
     * @return the new Transformer instance
     */
    public static <T, R> FlowableTransformer<T, R> flowableFlatMapStream(Function<? super T, ? extends Stream<R>> mapper) {
        return f -> f.flatMapIterable(v -> {
            Iterator<R> it = mapper.apply(v).iterator();
            return () -> it;
        });
    }

    public static <T, R> FlowableTransformer<T, R> flowableMapOptional(Function<? super T, Optional<R>> mapper) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

}
