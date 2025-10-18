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

import com.jetbrains.python.psi.PyTryExceptStatement;
import consulo.document.util.TextRange;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.localize.LocalizeValue;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:52:06 PM
 */
public class PyWithTryFinallySurrounder extends PyWithTryExceptSurrounder {
  public LocalizeValue getTemplateDescription() {
    return CodeInsightLocalize.surroundWithTryFinallyTemplate();
  }

  @Override
  protected String getTemplate() {
    return "try:\n    pass\nfinally:\n    pass";
  }

  @Override
  protected TextRange getResultRange(PyTryExceptStatement tryStatement) {
    return tryStatement.getFinallyPart().getStatementList().getTextRange();
  }
}
