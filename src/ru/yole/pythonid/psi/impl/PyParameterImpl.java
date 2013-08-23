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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PyElementTypes;
import ru.yole.pythonid.PyTokenTypes;
import ru.yole.pythonid.psi.PyElementGenerator;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyParameter;

public class PyParameterImpl extends PyElementImpl
  implements PyParameter
{
  public PyParameterImpl(ASTNode astNode, AbstractPythonLanguage language)
  {
    super(astNode, language);
  }
  @Nullable
  public String getName() {
    ASTNode node = getNode().findChildByType(getPyTokenTypes().IDENTIFIER);
    return node != null ? node.getText() : null;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
    getNode().replaceChild(getNode().getFirstChildNode(), nameElement);
    return this;
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParameter(this);
  }

  public boolean isPositionalContainer() {
    return getNode().findChildByType(getPyTokenTypes().MULT) != null;
  }

  public boolean isKeywordContainer() {
    return getNode().findChildByType(getPyTokenTypes().EXP) != null;
  }
  @Nullable
  public PyExpression getDefaultValue() {
    ASTNode[] nodes = getNode().getChildren(getPyElementTypes().EXPRESSIONS);
    if (nodes.length > 0) {
      return (PyExpression)nodes[0].getPsi();
    }
    return null;
  }
}