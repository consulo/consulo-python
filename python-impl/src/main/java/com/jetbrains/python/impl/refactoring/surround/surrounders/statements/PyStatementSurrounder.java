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

package com.jetbrains.python.impl.refactoring.surround.surrounders.statements;

import jakarta.annotation.Nonnull;

import consulo.language.editor.surroundWith.Surrounder;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 7:16:23 PM
 */
public abstract class PyStatementSurrounder implements Surrounder {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.refactoring.surround.surrounders.statements.PyStatementSurrounder");

  public boolean isApplicable(@Nonnull PsiElement[] elements) {
    return true;
  }

  @Nullable
  protected abstract TextRange surroundStatement(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements)
    throws IncorrectOperationException;

  public TextRange surroundElements(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements)
    throws IncorrectOperationException {
    return surroundStatement(project, editor, elements);
  }
}
