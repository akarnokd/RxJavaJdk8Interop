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

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.junit.*;

import io.reactivex.Completable;
import io.reactivex.observers.TestObserver;

public class CompletableInteropTest {

    @Test
    public void utilityClass() {
        TestHelper.checkUtilityClass(CompletableInterop.class);
    }

    @Test
    public void await() {
        TestHelper.assertFuture(null, Completable.complete()
        .to(CompletableInterop.await()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void awaitError() {
        TestHelper.assertFuture(null, Completable.error(new IllegalArgumentException())
        .to(CompletableInterop.await()));
    }


    @Test
    public void toStreamEmpty() {
        List<Integer> list = Completable.complete()
        .to(CompletableInterop.<Integer>toStream())
        .collect(Collectors.toList());
    
        Assert.assertTrue(list.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toStreamError() {
        Completable.error(new IllegalArgumentException())
        .to(CompletableInterop.toStream())
        .collect(Collectors.toList());
    }

    @Test
    public void fromFuture() {
        CompletableInterop.fromFuture(CompletableFuture.supplyAsync(() -> 1))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult();
    }

    @Test
    public void fromFutureEmpty() {
        CompletableInterop.fromFuture(CompletableFuture.supplyAsync(() -> null))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult();

        CompletableInterop.fromFuture(CompletableFuture.runAsync(() -> { }))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult();
    }

    @Test
    public void fromFutureError() {
        TestObserver<Object> ts = MaybeInterop.fromFuture(
                CompletableFuture.supplyAsync(() -> { throw new IllegalArgumentException(); }))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertFailure(CompletionException.class);

        Throwable c = ts.errors().get(0).getCause();
        Assert.assertTrue(c.toString(), c instanceof IllegalArgumentException);
    }

}
