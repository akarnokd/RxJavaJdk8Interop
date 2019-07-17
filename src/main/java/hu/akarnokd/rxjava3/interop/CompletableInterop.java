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

import java.util.concurrent.*;
import java.util.stream.Stream;

import io.reactivex.*;
import io.reactivex.subjects.CompletableSubject;

/**
 * Utility methods, sources and operators supporting RxJava 2 and the Jdk 8 API
 * interoperation.
 * 
 * @since 0.1.0
 */
public final class CompletableInterop {

    /** Utility class. */
    private CompletableInterop() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Returns a CompletionStage that signals a null value or error if the Completable terminates.
     * @param <T> the target value type (unused)
     * @return the converter function to be used with {@code Completable.to()}
     */
    public static <T> CompletableConverter<CompletionStage<T>> await() {
        return c -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            c.subscribe(() -> cf.complete(null), cf::completeExceptionally);
            return cf;
        };
    }

    /**
     * Returns a blocking Stream that waits for the Completable's terminal event.
     * @param <T> the value type
     * @return the converter function to be used with {@code Completable.to()}
     */
    public static <T> CompletableConverter<Stream<T>> toStream() {
        return c -> {
            ZeroOneIterator<T> zoi = new ZeroOneIterator<>();
            c.subscribe(zoi);
            return ZeroOneIterator.toStream(zoi);
        };
    }

    /**
     * Returns a Completable that terminates when the given CompletionStage terminates.
     * @param future the source CompletionStage instance
     * @return the new Completable instance
     */
    public static Completable fromFuture(CompletionStage<?> future) {
        CompletableSubject cs = CompletableSubject.create();

        future.whenComplete((v, e) -> {
            if (e != null) {
                cs.onError(e);
            } else {
                cs.onComplete();
            }
        });

        return cs;
    }
}
