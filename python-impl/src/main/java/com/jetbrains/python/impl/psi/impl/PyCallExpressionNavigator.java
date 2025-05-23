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

package com.jetbrains.python.impl.psi.impl;

import jakarta.annotation.Nullable;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.PyCallExpression;

/**
 * @author oleg
 */
public class PyCallExpressionNavigator {
  private PyCallExpressionNavigator() {
  }

  @Nullable
  public static PyCallExpression getPyCallExpressionByCallee(final PsiElement element){
     final PsiElement parent = element.getParent();
    if (parent instanceof PyCallExpression){
      final PyCallExpression expression = (PyCallExpression)parent;
      return expression.getCallee() == element ? expression : null;
    }
    return null;
  }
}
