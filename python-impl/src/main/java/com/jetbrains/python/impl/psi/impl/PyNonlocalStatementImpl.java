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
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author yole
 */
public class PyNonlocalStatementImpl extends PyElementImpl implements PyNonlocalStatement {
  private static final TokenSet TARGET_EXPRESSION_SET = TokenSet.create(PyElementTypes.TARGET_EXPRESSION);

  public PyNonlocalStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public void acceptPyVisitor(PyElementVisitor visitor) {
    visitor.visitPyNonlocalStatement(this);
  }

  @Nonnull
  @Override
  public PyTargetExpression[] getVariables() {
    return childrenToPsi(TARGET_EXPRESSION_SET, PyTargetExpression.EMPTY_ARRAY);
  }
}
