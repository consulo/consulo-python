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

package com.jetbrains.python.impl.patterns;

import jakarta.annotation.Nonnull;

import consulo.language.pattern.InitialPatternCondition;
import consulo.language.pattern.PsiElementPattern;
import com.jetbrains.python.psi.PyElement;

/**
 * @author yole
 */
public class PyElementPattern<T extends PyElement, Self extends PyElementPattern<T, Self>> extends PsiElementPattern<T, Self> {
  public PyElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public PyElementPattern(@Nonnull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  public static class Capture<T extends PyElement> extends PyElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@Nonnull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}
