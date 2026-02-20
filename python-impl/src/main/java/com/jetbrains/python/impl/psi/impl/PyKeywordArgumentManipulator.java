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

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordArgument;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyKeywordArgumentManipulator extends AbstractElementManipulator<PyKeywordArgument> {
  @Override
  public PyKeywordArgument handleContentChange(PyKeywordArgument element,
                                               TextRange range,
                                               String newContent) throws IncorrectOperationException {
    ASTNode keywordNode = element.getKeywordNode();
    if (keywordNode != null && keywordNode.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).equals(range)) {
      LanguageLevel langLevel = LanguageLevel.forElement(element);
      PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
      PyCallExpression callExpression =
        (PyCallExpression)generator.createExpressionFromText(langLevel, "foo(" + newContent + "=None)");
      PyKeywordArgument kwArg = callExpression.getArgumentList().getKeywordArgument(newContent);
      element.getKeywordNode().getPsi().replace(kwArg.getKeywordNode().getPsi());
      return element;
    }
    throw new IncorrectOperationException("unsupported manipulation on keyword argument");
  }

  @Nonnull
  @Override
  public Class<PyKeywordArgument> getElementClass() {
    return PyKeywordArgument.class;
  }
}
