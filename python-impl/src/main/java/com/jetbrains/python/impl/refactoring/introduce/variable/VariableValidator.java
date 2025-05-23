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

package com.jetbrains.python.impl.refactoring.introduce.variable;

import jakarta.annotation.Nullable;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:45:40 PM
 */
public class VariableValidator extends IntroduceValidator {
  @Nullable
  public String check(String name, PsiElement psiElement) {
    if (isDefinedInScope(name, psiElement)) {
      return PyBundle.message("refactoring.introduce.variable.scope.error");
    }
    return null;
  }
}
