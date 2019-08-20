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

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.junit.*;

import hu.akarnokd.rxjava3.jdk8interop.SingleInterop;
import io.reactivex.rxjava3.core.Single;

public class SingleInteropTest {

    @Test
    public void utilityClass() {
        TestHelper.checkUtilityClass(SingleInterop.class);
    }


    @Test
    public void fromFuture() {
        SingleInterop.fromFuture(CompletableFuture.supplyAsync(() -> 1))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1);
    }

    @Test
    public void fromFutureEmpty() {
        SingleInterop.fromFuture(CompletableFuture.supplyAsync(() -> null))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertFailure(NoSuchElementException.class);

        SingleInterop.fromFuture(CompletableFuture.runAsync(() -> { }))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertFailure(NoSuchElementException.class);
    }

    @Test
    public void fromFutureError() {
        TestObserverEx<Object> ts = new TestObserverEx<>();
        
        SingleInterop.fromFuture(
                CompletableFuture.supplyAsync(() -> { throw new IllegalArgumentException(); }))
        .subscribeWith(ts)
        .awaitDone(5, TimeUnit.SECONDS)
        .assertFailure(CompletionException.class);

        Throwable c = ts.errors().get(0).getCause();
        Assert.assertTrue(c.toString(), c instanceof IllegalArgumentException);
    }

    @Test
    public void get() {
        TestHelper.assertFuture(1, Single.just(1)
        .to(SingleInterop.get()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getError() {
        TestHelper.assertFuture(null, Single.error(new IllegalArgumentException())
        .to(SingleInterop.get()));
    }

    @Test
    public void toStream() {
        List<Integer> list = Single.just(1)
        .to(SingleInterop.toStream())
        .collect(Collectors.toList());

        Assert.assertEquals(Arrays.asList(1), list);
    }

    @Test(expected = IllegalArgumentException.class)
    public void toStreamError() {
        Single.<Integer>error(new IllegalArgumentException())
        .to(SingleInterop.toStream())
        .collect(Collectors.toList());
    }
}
