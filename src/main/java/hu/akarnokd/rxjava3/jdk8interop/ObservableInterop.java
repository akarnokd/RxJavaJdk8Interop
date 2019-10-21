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
import io.reactivex.rxjava3.internal.functions.ObjectHelper;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subjects.AsyncSubject;

/**
 * Utility methods, sources and operators supporting RxJava 2 and the Jdk 8 API
 * interoperation.
 * 
 * @since 0.1.0
 */
public final class ObservableInterop {

    /** Utility class. */
    private ObservableInterop() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Wrap a Stream into a Observable.
     * <p>
     * Note that Streams can only be consumed once and non-concurrently.
     * <p>
     * The operator closes the stream. Exceptions thrown by Stream.close()
     * are routed to the global RxJavaPlugins.onError handler.
     * @param <T> the value type
     * @param stream the source Stream
     * @return the new Observable instance
     */
    public static <T> Observable<T> fromStream(Stream<T> stream) {
        ObjectHelper.requireNonNull(stream, "stream is null");
        return RxJavaPlugins.onAssembly(new ObservableFromStream<>(stream));
    }

    /**
     * Returns a Observable for the value (or lack of) in the given Optional.
     * @param <T> the value type
     * @param opt the optional to wrap
     * @return the new Observable instance
     */
    public static <T> Observable<T> fromOptional(Optional<T> opt) {
        return opt.map(Observable::just).orElseGet(Observable::empty);
    }

    /**
     * Create a Observable that signals the terminal value or error of the given
     * CompletionStage.
     * <p>Cancelling the Observable subscription doesn't cancel the CompletionStage.
     * @param <T> the value type
     * @param cs the CompletionStage instance
     * @return the new Observable instance
     */
    public static <T> Observable<T> fromFuture(CompletionStage<T> cs) {
        AsyncSubject<T> ap = AsyncSubject.create();
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
     * Collect the elements of the Observable via the help of Collector and its callback
     * functions.
     * @param <T> the upstream value type
     * @param <A> the accumulated type
     * @param <R> the result type
     * @param collector the Collector object providing the callbacks
     * @return the Transformer instance to be used with {@code Observable.compose()}
     */
    public static <T, A, R> ObservableTransformer<T, R> collect(Collector<T, A, R> collector) {
        return f -> RxJavaPlugins.onAssembly(new ObservableCollector<>(f, collector));
    }

    /**
     * Returns a CompletionStage that signals the first element of the Observable
     * or a NoSuchElementException if the Observable is empty.
     * @param <T> the value type
     * @return the converter function to be used via {@code Observable.to}.
     */
    public static <T> ObservableConverter<T, CompletionStage<T>> first() {
        return f -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            f.firstOrError().subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a CompletionStage that signals the single element of the Observable,
     * IllegalArgumentException if the Observable is longer than 1 element
     * or a NoSuchElementException if the Observable is empty.
     * @param <T> the value type
     * @return the converter function to be used with {@code Observable.to}.
     */
    public static <T> ObservableConverter<T, CompletionStage<T>> single() {
        return f -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            f.singleOrError().subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a CompletionStage that emits the last element of the Observable or
     * NoSuchElementException if the Observable is empty.
     * @param <T> the value type
     * @return the converter function to be used with {@code Observable.to}.
     */
    public static <T> ObservableConverter<T, CompletionStage<T>> last() {
        return f -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            f.lastOrError().subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a blocking Stream of the elements of the Observable.
     * <p>
     * Closing the Stream will cancel the flow.
     * @param <T> the value type
     * @return the converter function to be used with {@code Observable.to}.
     */
    public static <T> ObservableConverter<T, Stream<T>> toStream() {
        return f -> ZeroOneIterator.toStream(f.blockingIterable().iterator());
    }

    /**
     * Block until the source Observable emits its first item and return that as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Observable.to()}.
     */
    public static <T> ObservableConverter<T, Optional<T>> firstElement() {
        return o -> Optional.ofNullable(o.blockingFirst(null));
    }

    /**
     * Block until the source Observable completes and return its last value as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Observable.to()}.
     */
    public static <T> ObservableConverter<T, Optional<T>> lastElement() {
        return o -> Optional.ofNullable(o.blockingLast(null));
    }

    /**
     * Maps the upstream value into an optional and extracts its optional value to be emitted towards
     * the downstream if present.
     * @param <T> the upstream value type
     * @param <R> the result value type
     * @param mapper the function receiving the upstream value and should return an Optional
     * @return the Transformer instance to be used with {@code Observable.compose()}
     */
    public static <T, R> ObservableTransformer<T, R> mapOptional(Function<? super T, Optional<R>> mapper) {
        return f -> RxJavaPlugins.onAssembly(new ObservableMapOptional<>(f, mapper));
    }

    /**
     * Map each value of the upstream into a Stream and flatten them into a single sequence.
     * @param <T> the input value type
     * @param <R> the Stream type
     * @param mapper the function that returns a Stream for each upstream value
     * @return the new Transformer instance
     */
    public static <T, R> ObservableTransformer<T, R> flatMapStream(Function<? super T, ? extends Stream<R>> mapper) {
        return o -> o.concatMap(v -> fromStream(mapper.apply(v)));
    }

}
