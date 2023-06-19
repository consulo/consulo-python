/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.types.PyGenericType;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {
  public PyBinaryExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }

  @Nullable
  public PyExpression getLeftExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyExpression getRightExpression() {
    return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
  }

  @Nullable
  public PyElementType getOperator() {
    final PsiElement psiOperator = getPsiOperator();
    return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
  }

  @Nullable
  public PsiElement getPsiOperator() {
    ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyElementTypes.BINARY_OPS);
    if (child != null) {
      return child.getPsi();
    }
    return null;
  }

  public boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuilder buf = new StringBuilder();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (elType instanceof PyElementType && PyElementTypes.BINARY_OPS.contains(elType)) {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

  @Nullable
  public PyExpression getOppositeExpression(PyExpression expression) throws IllegalArgumentException {
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

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    PyExpression left = getLeftExpression();
    PyExpression right = getRightExpression();
    if (left == child.getPsi() && right != null) {
      replace(right);
    }
    else if (right == child.getPsi() && left != null) {
      replace(left);
    }
    else {
      throw new IncorrectOperationException("Element " + child.getPsi() + " is neither left expression or right expression");
    }
  }

  @Nonnull
  @Override
  public PsiPolyVariantReference getReference() {
    return getReference(PyResolveContext.noImplicits());
  }

  @Nonnull
  @Override
  public PsiPolyVariantReference getReference(PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
    if (isOperator("and") || isOperator("or")) {
      final PyExpression left = getLeftExpression();
      final PyType leftType = left != null ? context.getType(left) : null;
      final PyExpression right = getRightExpression();
      final PyType rightType = right != null ? context.getType(right) : null;
      if (leftType == null && rightType == null) {
        return null;
      }
      return PyUnionType.union(leftType, rightType);
    }
    final List<PyTypeChecker.AnalyzeCallResults> results = PyTypeChecker.analyzeCallSite(this, context);
    if (!results.isEmpty()) {
      final List<PyType> types = new ArrayList<>();
      final List<PyType> matchedTypes = new ArrayList<>();
      for (PyTypeChecker.AnalyzeCallResults result : results) {
        boolean matched = true;
        for (Map.Entry<PyExpression, PyNamedParameter> entry : result.getArguments().entrySet()) {
          final PyExpression argument = entry.getKey();
          final PyNamedParameter parameter = entry.getValue();
          if (parameter.isPositionalContainer() || parameter.isKeywordContainer()) {
            continue;
          }
          final Map<PyGenericType, PyType> substitutions = new HashMap<>();
          final PyType parameterType = context.getType(parameter);
          final PyType argumentType = context.getType(argument);
          if (!PyTypeChecker.match(parameterType, argumentType, context, substitutions)) {
            matched = false;
          }
        }
        final PyType type = result.getCallable().getCallType(context, this);
        if (!PyTypeChecker.isUnknown(type) && !(type instanceof PyNoneType)) {
          types.add(type);
          if (matched) {
            matchedTypes.add(type);
          }
        }
      }
      if (!matchedTypes.isEmpty()) {
        return PyUnionType.union(matchedTypes);
      }
      if (!types.isEmpty()) {
        return PyUnionType.union(types);
      }
    }
    String referencedName = getReferencedName();
    if (referencedName != null && PyNames.COMPARISON_OPERATORS.contains(referencedName)) {
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    return null;
  }

  @Override
  public PyExpression getQualifier() {
    return getLeftExpression();
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    return PyPsiUtils.asQualifiedName(this);
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public String getReferencedName() {
    final PyElementType t = getOperator();
    return t != null ? t.getSpecialMethodName() : null;
  }

  @Override
  public ASTNode getNameElement() {
    final PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }
}
