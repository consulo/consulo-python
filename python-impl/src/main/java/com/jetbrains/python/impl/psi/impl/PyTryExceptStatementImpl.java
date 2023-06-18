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

import javax.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class PyTryExceptStatementImpl extends PyPartitionedElementImpl implements PyTryExceptStatement {
  private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_PART);

  public PyTryExceptStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTryExceptStatement(this);
  }

  @Nonnull
  public PyExceptPart[] getExceptParts() {
    return childrenToPsi(EXCEPT_BLOCKS, PyExceptPart.EMPTY_ARRAY);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @Nonnull
  public PyTryPart getTryPart() {
    return (PyTryPart)getPartNotNull(PyElementTypes.TRY_PART);
  }


  public PyFinallyPart getFinallyPart() {
    return (PyFinallyPart)getPart(PyElementTypes.FINALLY_PART);
  }
}
