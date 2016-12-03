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
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.junit.*;

import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;

public class MaybeInteropTest {

    @Test
    public void utilityClass() {
        TestHelper.checkUtilityClass(MaybeInterop.class);
    }

    @Test
    public void fromOptional() {
        MaybeInterop.fromOptional(Optional.of(1))
        .test()
        .assertResult(1);

        MaybeInterop.fromOptional(Optional.empty())
        .test()
        .assertResult();
    }

    @Test
    public void fromFuture() {
        MaybeInterop.fromFuture(CompletableFuture.supplyAsync(() -> 1))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1);
    }

    @Test
    public void fromFutureEmpty() {
        MaybeInterop.fromFuture(CompletableFuture.supplyAsync(() -> null))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult();

        MaybeInterop.fromFuture(CompletableFuture.runAsync(() -> { }))
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

    @Test
    public void get() {
        TestHelper.assertFuture(1, Maybe.just(1)
        .to(MaybeInterop.get()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getError() {
        TestHelper.assertFuture(null, Maybe.error(new IllegalArgumentException())
        .to(MaybeInterop.get()));
    }

    @Test
    public void getEmpty() {
        TestHelper.assertFuture(null, Maybe.empty()
        .to(MaybeInterop.get()));
    }

    @Test
    public void toStream() {
        List<Integer> list = Maybe.just(1)
        .to(MaybeInterop.toStream())
        .collect(Collectors.toList());
    
        Assert.assertEquals(Arrays.asList(1), list);
    }

    @Test
    public void toStreamEmpty() {
        List<Integer> list = Maybe.<Integer>empty()
        .to(MaybeInterop.toStream())
        .collect(Collectors.toList());
    
        Assert.assertTrue(list.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toStreamError() {
        Maybe.<Integer>error(new IllegalArgumentException())
        .to(MaybeInterop.toStream())
        .collect(Collectors.toList());
    }

    @Test
    public void element() {
        Assert.assertEquals((Integer)1, Maybe.just(1)
        .to(MaybeInterop.element()).get());
    }

    @Test
    public void elementEmpty() {
        Assert.assertFalse(Maybe.empty().to(MaybeInterop.element()).isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void elementError() {
        Maybe.<Integer>error(new IllegalArgumentException())
        .to(MaybeInterop.element())
        .get();
    }

    @Test
    public void mapOptional() {
        Maybe.just(1)
        .compose(MaybeInterop.mapOptional(v -> Optional.of(-v)))
        .test()
        .assertResult(-1);
    }

    @Test
    public void mapOptionalEmpty() {
        Maybe.just(1)
        .compose(MaybeInterop.mapOptional(v -> Optional.empty()))
        .test()
        .assertResult();
    }

    @Test
    public void mapOptionalEmpty2() {
        Maybe.<Integer>empty()
        .compose(MaybeInterop.mapOptional(v -> Optional.of(-v)))
        .test()
        .assertResult();
    }

    @Test
    public void mapOptionalError() {
        Maybe.<Integer>error(new IllegalArgumentException())
        .compose(MaybeInterop.mapOptional(v -> Optional.of(-v)))
        .test()
        .assertFailure(IllegalArgumentException.class);
    }
}
