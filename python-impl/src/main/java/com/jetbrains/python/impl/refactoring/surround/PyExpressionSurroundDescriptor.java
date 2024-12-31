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

package com.jetbrains.python.impl.refactoring.surround;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.surround.surrounders.expressions.PyWithParenthesesSurrounder;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.surroundWith.SurroundDescriptor;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 5:59:04 PM
 */
@ExtensionImpl
public class PyExpressionSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {new PyWithParenthesesSurrounder()};

  @Nonnull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiElement element = PyRefactoringUtil.findExpressionInRange(file, startOffset, endOffset);
    if (!(element instanceof PyExpression)) {
      return PsiElement.EMPTY_ARRAY;
    }
    return new PsiElement[]{element};
  }

  @Nonnull
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
