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

package com.jetbrains.python.impl.refactoring.introduce;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import consulo.document.util.TextRange;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.Pair;

import org.jspecify.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 * @since 2009-08-19
 */
public abstract class IntroduceValidator {
  private final NamesValidator myNamesValidator = NamesValidator.forLanguage(PythonLanguage.INSTANCE);

    public boolean isNameValid(String name, Project project) {
    return (name != null) &&
           (myNamesValidator.isIdentifier(name, project)) &&
           !(myNamesValidator.isKeyword(name, project));
  }

  public boolean checkPossibleName(String name, PyExpression expression) {
    return check(name, expression) == null;
  }

  @Nullable
  public abstract String check(String name, PsiElement psiElement);

  public static boolean isDefinedInScope(String name, PsiElement psiElement) {
    if (psiElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE) != null) {
      Pair<PsiElement,TextRange> data = psiElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
      psiElement = data.first;
    }
    PsiElement context = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    }
    if (context == null) {
      context = psiElement.getContainingFile();
    }

    return PyRefactoringUtil.collectUsedNames(context).contains(name) || PyBuiltinCache.getInstance(psiElement).getByName(name) != null;
  }
}
