/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.impl.psi.types.functionalParser;

import consulo.util.lang.Pair;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

/**
* @author vlan
*/
public interface FunctionalParser<R, T> {
  @Nonnull
  R parse(@Nonnull List<Token<T>> tokens) throws ParserException;

  @Nonnull
  Pair<R, State> parse(@Nonnull List<Token<T>> tokens,
                                            @Nonnull FunctionalParserBase.State state) throws ParserException;

  @Nonnull
  <R2> FunctionalParser<Pair<R, R2>, T> then(@Nonnull FunctionalParser<R2, T> parser);

  @Nonnull
  <R2> FunctionalParser<R2, T> skipThen(@Nonnull FunctionalParser<R2, T> parser);

  @Nonnull
  <R2> FunctionalParser<R, T> thenSkip(@Nonnull FunctionalParser<R2, T> parser);

  @Nonnull
  FunctionalParser<R, T> or(@Nonnull FunctionalParser<R, T> parser);

  @Nonnull
  <R2> FunctionalParser<R2, T> map(@Nonnull Function<R, R2> f);

  @Nonnull
  FunctionalParser<R, T> endOfInput();

  @Nonnull
  FunctionalParser<R, T> named(@Nonnull String name);

  @Nonnull
  FunctionalParser<R, T> cached();

  class State {
    private final int myPos;
    private final int myMax;
    private final Object myKey;

    State() {
      myKey = new Object();
      myPos = 0;
      myMax = 0;
    }

    State(@Nonnull State state, int pos, int max) {
      myKey = state.myKey;
      myPos = pos;
      myMax = max;
    }

    public int getPos() {
      return myPos;
    }

    public int getMax() {
      return myMax;
    }

    public Object getKey() {
      return myKey;
    }
  }
}
