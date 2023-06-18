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

import javax.annotation.Nonnull;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:23:51 PM
 */
public class PyWithTryExceptSurrounder extends PyStatementSurrounder {
  @Override
  @Nullable
  protected TextRange surroundStatement(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements)
    throws IncorrectOperationException
  {
    PyTryExceptStatement tryStatement = PyElementGenerator.getInstance(project).
      createFromText(LanguageLevel.getDefault(), PyTryExceptStatement.class, getTemplate());
    final PsiElement parent = elements[0].getParent();
    final PyStatementList statementList = tryStatement.getTryPart().getStatementList();
    assert statementList != null;
    statementList.addRange(elements[0], elements[elements.length - 1]);
    statementList.getFirstChild().delete();
    tryStatement = (PyTryExceptStatement)parent.addBefore(tryStatement, elements[0]);
    parent.deleteChildRange(elements [0], elements[elements.length-1]);

    final PsiFile psiFile = parent.getContainingFile();
    final Document document = psiFile.getViewProvider().getDocument();
    final RangeMarker rangeMarker = document.createRangeMarker(tryStatement.getTextRange());

    CodeStyleManager.getInstance(project).reformat(psiFile);
    final PsiElement element = psiFile.findElementAt(rangeMarker.getStartOffset());
    tryStatement = PsiTreeUtil.getParentOfType(element, PyTryExceptStatement.class);
    if (tryStatement != null) {
      return getResultRange(tryStatement);
    }
    return null;
  }

  protected String getTemplate() {
    return "try:\n    pass\nexcept:\n    pass";
  }

  protected TextRange getResultRange(PyTryExceptStatement tryStatement) {
    final PyExceptPart part = tryStatement.getExceptParts()[0];
    return part.getStatementList().getTextRange();
  }

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.try.except.template");
  }
}
