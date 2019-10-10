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

import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.*;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.processors.AsyncProcessor;

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

    /**
     * Wrap a Stream into a Flowable.
     * <p>Note that Streams can only be consumed once and non-concurrently.
     * @param <T> the value type
     * @param stream the source Stream
     * @return the new Flowable instance
     */
    public static <T> Flowable<T> fromStream(Stream<T> stream) {
        return RxJavaPlugins.onAssembly(new FlowableFromStream<T>(stream));
    }

    /**
     * Returns a Flowable for the value (or lack of) in the given Optional.
     * @param <T> the value type
     * @param opt the optional to wrap
     * @return the new Flowable instance
     */
    public static <T> Flowable<T> fromOptional(Optional<T> opt) {
        return opt.map(Flowable::just).orElseGet(Flowable::empty);
    }

    /**
     * Create a Flowable that signals the terminal value or error of the given
     * CompletionStage.
     * <p>Cancelling the Flowable subscription doesn't cancel the CompletionStage.
     * @param <T> the value type
     * @param cs the CompletionStage instance
     * @return the new Flowable instance
     */
    public static <T> Flowable<T> fromFuture(CompletionStage<T> cs) {
        AsyncProcessor<T> ap = AsyncProcessor.create();
        cs.whenComplete((v, e) -> {
            if (e != null) {
                ap.onError(e);
            } else {
                ap.onNext(v);
                ap.onComplete();
            }
        });
        return ap;
    }

    /**
     * Collect the elements of the Flowable via the help of Collector and its callback
     * functions.
     * @param <T> the upstream value type
     * @param <A> the accumulated type
     * @param <R> the result type
     * @param collector the Collector object providing the callbacks
     * @return the Transformer instance to be used with {@code Flowable.compose()}
     */
    public static <T, A, R> FlowableTransformer<T, R> collect(Collector<T, A, R> collector) {
        return f -> RxJavaPlugins.onAssembly(new FlowableCollector<>(f, collector));
    }

    /**
     * Returns a CompletionStage that signals the first element of the Flowable
     * or a NoSuchElementException if the Flowable is empty.
     * @param <T> the value type
     * @return the converter function to be used via {@code Flowable.to}.
     */
    public static <T> FlowableConverter<T, CompletionStage<T>> first() {
        return f -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            f.firstOrError().subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a CompletionStage that signals the single element of the Flowable,
     * IllegalArgumentException if the Flowable is longer than 1 element
     * or a NoSuchElementException if the Flowable is empty.
     * @param <T> the value type
     * @return the converter function to be used with {@code Flowable.to}.
     */
    public static <T> FlowableConverter<T, CompletionStage<T>> single() {
        return f -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            f.singleOrError().subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a CompletionStage that emits the last element of the Flowable or
     * NoSuchElementException if the Flowable is empty.
     * @param <T> the value type
     * @return the converter function to be used with {@code Flowable.to}.
     */
    public static <T> FlowableConverter<T, CompletionStage<T>> last() {
        return f -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            f.lastOrError().subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a blocking Stream of the elements of the Flowable.
     * <p>
     * Closing the Stream will cancel the flow.
     * @param <T> the value type
     * @return the converter function to be used with {@code Flowable.to}.
     */
    public static <T> FlowableConverter<T, Stream<T>> toStream() {
        return f -> ZeroOneIterator.toStream(f.blockingIterable().iterator());
    }

    /**
     * Block until the source Flowable emits its first item and return that as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Flowable.to()}.
     */
    public static <T> FlowableConverter<T, Optional<T>> firstElement() {
        return f -> Optional.ofNullable(f.blockingFirst(null));
    }

    /**
     * Block until the source Flowable completes and return its last value as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Flowable.to()}.
     */
    public static <T> FlowableConverter<T, Optional<T>> lastElement() {
        return f -> Optional.ofNullable(f.blockingLast(null));
    }

    /**
     * Map each value of the upstream into a Stream and flatten them into a single sequence.
     * @param <T> the input value type
     * @param <R> the Stream type
     * @param mapper the function that returns a Stream for each upstream value
     * @return the Transformer instance to be used with {@code Flowable.compose()}
     */
    public static <T, R> FlowableTransformer<T, R> flatMapStream(Function<? super T, ? extends Stream<R>> mapper) {
        return f -> f.concatMap(v -> fromStream(mapper.apply(v)));
    }

    /**
     * Maps the upstream value into an optional and extracts its optional value to be emitted towards
     * the downstream if present.
     * @param <T> the upstream value type
     * @param <R> the result value type
     * @param mapper the function receiving the upstream value and should return an Optional
     * @return the Transformer instance to be used with {@code Flowable.compose()}
     */
    public static <T, R> FlowableTransformer<T, R> mapOptional(Function<? super T, Optional<R>> mapper) {
        return f -> RxJavaPlugins.onAssembly(new FlowableMapOptional<>(f, mapper));
    }

}
