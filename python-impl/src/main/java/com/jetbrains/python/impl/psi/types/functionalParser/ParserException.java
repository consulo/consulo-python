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

import javax.annotation.Nonnull;

/**
* @author vlan
*/
public class ParserException extends Exception {
  @Nonnull
  private final FunctionalParserBase.State myState;

  public ParserException(@Nonnull String message, @Nonnull FunctionalParserBase.State state) {
    super(message);
    myState = state;
  }

  @Nonnull
  FunctionalParserBase.State getState() {
    return myState;
  }
}
