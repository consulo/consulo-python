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

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class PyKeywordArgumentImpl extends PyElementImpl implements PyKeywordArgument {
  public PyKeywordArgumentImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public String getKeyword() {
    ASTNode node = getKeywordNode();
    return node != null ? node.getText() : null;
  }

  @Override
  public ASTNode getKeywordNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public PyExpression getValueExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + getKeyword();
  }

  public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
    final PyExpression e = getValueExpression();
    return e != null ? context.getType(e) : null;
  }

  @Override
  public PsiReference getReference() {
    final ASTNode keywordNode = getKeywordNode();
    if (keywordNode != null) {
      return new PyKeywordArgumentReference(this, keywordNode.getTextRange().shiftRight(-getTextRange().getStartOffset()));
    }
    return null;
  }

  @Override
  public String getName() {
    return getKeyword();
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException
  {
    PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
    PyExpression expression = getValueExpression();
    PyKeywordArgument keywordArgument = generator.createKeywordArgument(LanguageLevel.forElement(this), name,
                                                                        expression != null ? expression.getText() : name);
    getNode().replaceChild(getKeywordNode(), keywordArgument.getKeywordNode());
    return this;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordArgument(this);
  }
}
