/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PyTokenTypes;
import ru.yole.pythonid.psi.PsiCached;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyKeywordArgument;

public class PyKeywordArgumentImpl extends PyElementImpl
  implements PyKeywordArgument
{
  public PyKeywordArgumentImpl(ASTNode astNode, AbstractPythonLanguage language)
  {
    super(astNode, language);
  }
  @PsiCached
  @Nullable
  public String getKeyword() {
    ASTNode node = getKeywordNode();
    return node != null ? node.getText() : null;
  }

  @PsiCached
  public ASTNode getKeywordNode() {
    return getNode().findChildByType(getPyTokenTypes().IDENTIFIER);
  }

  @PsiCached
  public PyExpression getValueExpression() {
    return (PyExpression)PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public String toString()
  {
    return getClass().getSimpleName() + ": " + getKeyword();
  }
  @Nullable
  protected Class<? extends PsiElement> getValidChildClass() {
    return PyExpression.class;
  }
}