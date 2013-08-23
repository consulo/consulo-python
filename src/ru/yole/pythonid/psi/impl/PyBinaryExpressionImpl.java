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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PyTokenTypes;
import ru.yole.pythonid.psi.PsiCached;
import ru.yole.pythonid.psi.PyBinaryExpression;
import ru.yole.pythonid.psi.PyElement;
import ru.yole.pythonid.psi.PyElementType;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;

public class PyBinaryExpressionImpl extends PyElementImpl
  implements PyBinaryExpression
{
  private final TokenSet BINARY_OPS = TokenSet.create(new IElementType[] { getPyTokenTypes().OR_KEYWORD, getPyTokenTypes().AND_KEYWORD, getPyTokenTypes().LT, getPyTokenTypes().GT, getPyTokenTypes().OR, getPyTokenTypes().XOR, getPyTokenTypes().AND, getPyTokenTypes().LTLT, getPyTokenTypes().GTGT, getPyTokenTypes().EQEQ, getPyTokenTypes().GE, getPyTokenTypes().LE, getPyTokenTypes().NE, getPyTokenTypes().NE_OLD, getPyTokenTypes().IN_KEYWORD, getPyTokenTypes().IS_KEYWORD, getPyTokenTypes().NOT_KEYWORD, getPyTokenTypes().PLUS, getPyTokenTypes().MINUS, getPyTokenTypes().MULT, getPyTokenTypes().FLOORDIV, getPyTokenTypes().DIV, getPyTokenTypes().PERC });

  public PyBinaryExpressionImpl(ASTNode astNode, AbstractPythonLanguage language)
  {
    super(astNode, language);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor)
  {
    pyVisitor.visitPyBinaryExpression(this);
  }

  @PsiCached
  public PyExpression getLeftExpression() {
    return (PyExpression)PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @PsiCached
  public PyExpression getRightExpression() {
    return (PyExpression)PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
  }

  @PsiCached
  public List<PyElementType> getOperator()
  {
    List list = new ArrayList(3);
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (((elType instanceof PyElementTypeImpl)) && (this.BINARY_OPS.contains(elType)))
      {
        list.add((PyElementTypeImpl)elType);
      }
      child = child.getTreeNext();
    }
    return list;
  }

  @PsiCached
  public boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuffer buf = new StringBuffer();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (((elType instanceof PyElementTypeImpl)) && (this.BINARY_OPS.contains(elType)))
      {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

  public PyExpression getOppositeExpression(PyExpression expression) throws IllegalArgumentException
  {
    PyExpression right = getRightExpression();
    PyExpression left = getLeftExpression();
    if (expression.equals(left)) {
      return right;
    }
    if (expression.equals(right)) {
      return left;
    }
    throw new IllegalArgumentException("expression " + expression + " is neither left exp or right exp");
  }

  protected void deletePyChild(PyElementImpl element)
    throws IncorrectOperationException
  {
    PyExpression left = getLeftExpression();
    PyExpression right = getRightExpression();
    if (left == element)
      replace(right);
    else if (right == element)
      replace(left);
    else
      throw new IncorrectOperationException("Element " + element + " is neither left expression or right expression");
  }

  @Nullable
  protected Class<? extends PsiElement> getValidChildClass()
  {
    return PyElement.class;
  }
}