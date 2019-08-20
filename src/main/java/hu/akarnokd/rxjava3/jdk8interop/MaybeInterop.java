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
import java.util.concurrent.*;
import java.util.stream.Stream;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subjects.MaybeSubject;

/**
 * Utility methods, sources and operators supporting RxJava 2 and the Jdk 8 API
 * interoperation.
 * 
 * @since 0.1.0
 */
public final class MaybeInterop {

    /** Utility class. */
    private MaybeInterop() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Returns a Maybe that emits the value of the Optional or is
     * empty if the Optional is also empty.
     * @param <T> the value type
     * @param opt the Optional value
     * @return the new Maybe instance
     */
    public static <T> Maybe<T> fromOptional(Optional<T> opt) {
        return opt.map(Maybe::just).orElse(Maybe.empty());
    }

    /**
     * Returns a Maybe that emits the resulting value of the CompletionStage or
     * its error, treating null as empty source.
     * @param <T> the value type
     * @param cs the source CompletionStage instance
     * @return the new Maybe instance
     */
    public static <T> Maybe<T> fromFuture(CompletionStage<T> cs) {
        MaybeSubject<T> ms = MaybeSubject.create();
        cs.whenComplete((v, e) -> {
            if (e != null) {
                ms.onError(e);
            } else
            if (v != null) {
                ms.onSuccess(v);
            } else {
                ms.onComplete();
            }
        });
        return ms;
    }

    /**
     * Returns a CompletionStage that signals the single value or terminal event
     * of the given Maybe source.
     * <p>An empty Maybe will complete with a null value
     * @param <T> the value type
     * @return the converter function to be used with {@code Maybe.to()}
     */
    public static <T> MaybeConverter<T, CompletionStage<T>> get() {
        return m -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            m.subscribe(cf::complete, cf::completeExceptionally, () -> cf.complete(null));
            return cf;
        };
    }

    /**
     * Returns a blocking Stream of a potentially zero or one value (or error) of
     * the Maybe.
     * @param <T> the value type
     * @return the converter function to be used with {@code Maybe.to()}
     */
    public static <T> MaybeConverter<T, Stream<T>> toStream() {
        return m -> {
            ZeroOneIterator<T> zoi = new ZeroOneIterator<>();
            m.subscribe(zoi);
            return ZeroOneIterator.toStream(zoi);
        };
    }

    /**
     * Block until the source Maybe completes and return its possible value as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Maybe.to()}.
     */
    public static <T> MaybeConverter<T, Optional<T>> element() {
        return m -> Optional.ofNullable(m.blockingGet());
    }

    /**
     * Maps the upstream value into an optional and extracts its optional value to be emitted towards
     * the downstream if present.
     * @param <T> the upstream value type
     * @param <R> the result value type
     * @param mapper the function receiving the upstream value and should return an Optional
     * @return the Transformer instance to be used with {@code Flowable.compose()}
     */
    public static <T, R> MaybeTransformer<T, R> mapOptional(Function<? super T, Optional<R>> mapper) {
        return m -> RxJavaPlugins.onAssembly(new MaybeMapOptional<>(m, mapper));
    }

}
