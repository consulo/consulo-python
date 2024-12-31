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
import jakarta.annotation.Nonnull;

import java.util.List;

/**
* @author vlan
*/
public class ForwardDeclaration<R, T> extends FunctionalParserBase<R, T> {
  private FunctionalParser<R, T> myParser = null;

  @Nonnull
  public static <R, T> ForwardDeclaration<R, T> create() {
    return new ForwardDeclaration<R, T>();
  }

  @Nonnull
  public ForwardDeclaration<R, T> define(@Nonnull FunctionalParser<R, T> parser) {
    myParser = parser;
    return this;
  }

  @Nonnull
  @Override
  public Pair<R, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
    if (myParser != null) {
      return myParser.parse(tokens, state);
    }
    throw new IllegalStateException("Undefined forward parser declaration");
  }
}
