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

package com.jetbrains.python.impl.refactoring.introduce.field;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;

/**
 * @author Dennis.Ushakov
 */
public class IntroduceFieldValidator extends IntroduceValidator {
  @Override
  public String check(String name, PsiElement psiElement) {
    PyClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    if (containingClass == null) {
      return null;
    }
    if (containingClass.findInstanceAttribute(name, true) != null) {
      return PyBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
