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
import com.jetbrains.python.impl.refactoring.surround.surrounders.statements.*;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
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
 * Time: 7:09:36 PM
 */
@ExtensionImpl
public class PyStatementSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
    new PyWithIfSurrounder(),
    // new PyWithIfElseSurrounder(),
    new PyWithWhileSurrounder(),
    //new PyWithWhileElseSurrounder(),
    new PyWithReturnSurrounder(),
    new PyWithTryExceptSurrounder(),
    new PyWithTryFinallySurrounder()
  };

  @Nonnull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    PsiElement[] statements = PyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) {
      return PsiElement.EMPTY_ARRAY;
    }
    return statements;
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
