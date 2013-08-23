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
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PyElementTypes;
import ru.yole.pythonid.PyTokenTypes;
import ru.yole.pythonid.psi.PyElementGenerator;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyFunction;
import ru.yole.pythonid.psi.PyParameter;
import ru.yole.pythonid.psi.PyParameterList;
import ru.yole.pythonid.psi.PyStatementList;

public class PyFunctionImpl extends PyElementImpl
  implements PyFunction
{
  public PyFunctionImpl(ASTNode astNode, AbstractPythonLanguage language)
  {
    super(astNode, language);
  }
  @Nullable
  public String getName() {
    ASTNode node = getNameNode();
    return node != null ? node.getText() : null;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
    getNode().replaceChild(getNameNode(), nameElement);
    return this;
  }

  public Icon getIcon(int flags) {
    return Icons.METHOD_ICON;
  }

  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(getPyTokenTypes().IDENTIFIER);
  }

  @NotNull
  public PyParameterList getParameterList()
  {
    PyParameterList tmp14_11 = ((PyParameterList)childToPsiNotNull(getPyElementTypes().PARAMETER_LIST)); if (tmp14_11 == null) throw new IllegalStateException("@NotNull method must not return null"); return tmp14_11;
  }

  @NotNull
  public PyStatementList getStatementList()
  {
    PyStatementList tmp14_11 = ((PyStatementList)childToPsiNotNull(getPyElementTypes().STATEMENT_LIST)); if (tmp14_11 == null) throw new IllegalStateException("@NotNull method must not return null"); return tmp14_11;
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFunction(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place)
  {
    if ((lastParent != null) && (lastParent.getParent() == this)) {
      PyParameter[] params = getParameterList().getParameters();
      for (PyParameter param : params) {
        if (!processor.execute(param, substitutor)) return false;
      }
    }

    return processor.execute(this, substitutor);
  }

  public int getTextOffset() {
    ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public void delete() throws IncorrectOperationException {
    ASTNode node = getNode();
    node.getTreeParent().removeChild(node);
  }
}