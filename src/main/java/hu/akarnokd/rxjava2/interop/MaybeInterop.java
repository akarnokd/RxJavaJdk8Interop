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
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import io.reactivex.*;
import io.reactivex.functions.Function;

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

    public static <T> Observable<T> maybeFromOptional(Optional<T> opt) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Maybe<T> maybeFromFuture(CompletionStage<T> cs) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Function<Maybe<T>, CompletionStage<T>> maybeGet() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    public static <T> Function<Maybe<T>, Stream<T>> maybeToStream() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    /**
     * Block until the source Maybe completes and return its possible value as Optional.
     * @param <T> the value type
     * @return the converter Function to be used with {@code Maybe.to()}.
     */
    public static <T> Function<Maybe<T>, Optional<T>> maybeElement() {
        return m -> Optional.ofNullable(m.blockingGet());
    }

    public static <T, R> MaybeTransformer<T, R> maybeMapOptional(Function<? super T, Optional<R>> mapper) {
        // TODO implement
        throw new UnsupportedOperationException();
    }

}
