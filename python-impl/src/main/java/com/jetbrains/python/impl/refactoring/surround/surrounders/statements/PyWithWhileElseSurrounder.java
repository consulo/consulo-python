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

import consulo.language.editor.CodeInsightUtilCore;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyWhileStatement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 5:46:20 PM
 */
public class PyWithWhileElseSurrounder extends PyStatementSurrounder {
  @Override
  @Nullable
  protected TextRange surroundStatement(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements)
    throws IncorrectOperationException
  {
    PyWhileStatement whileStatement =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyWhileStatement.class, "while True:\n    \nelse:\n");
    final PsiElement parent = elements[0].getParent();
    whileStatement.addRange(elements[0], elements[elements.length - 1]);
    whileStatement = (PyWhileStatement) parent.addBefore(whileStatement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);

    whileStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(whileStatement);
    if (whileStatement == null) {
      return null;
    }
    return whileStatement.getTextRange();
  }

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.whileelse.template");
  }
}
