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

import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.stream.Stream;

import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.subjects.SingleSubject;

/**
 * Utility methods, sources and operators supporting RxJava 2 and the Jdk 8 API
 * interoperation.
 * 
 * @since 0.1.0
 */
public final class SingleInterop {

    /** Utility class. */
    private SingleInterop() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Returns a CompletionStage that signals the success value or error of the
     * source Single.
     * @param <T> the value type
     * @return the new Function to be used with {@code Single.to()}
     */
    public static <T> Function<Single<T>, CompletionStage<T>> get() {
        return c -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            c.subscribe(cf::complete, cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a blocking Stream of the single success value of the source Single.
     * @param <T> the value type
     * @return the new Function to be used with {@code Single.to()}
     */
    public static <T> Function<Single<T>, Stream<T>> toStream() {
        return s -> {
            ZeroOneIterator<T> zoi = new ZeroOneIterator<>();
            s.subscribe(zoi);
            return ZeroOneIterator.toStream(zoi);
        };
    }

    /**
     * Returns a Single that emits the value of the CompletionStage, its error or
     * NoSuchElementException if it signals null.
     * @param <T> the value type
     * @param future the source CompletionStage instance
     * @return the new Completable instance
     */
    public static <T> Single<T> fromFuture(CompletionStage<T> future) {
        SingleSubject<T> cs = SingleSubject.create();

        future.whenComplete((v, e) -> {
            if (e != null) {
                cs.onError(e);
            } else 
            if (v != null) {
                cs.onSuccess(v);
            } else {
                cs.onError(new NoSuchElementException());
            }
        });

        return cs;
    }

}
