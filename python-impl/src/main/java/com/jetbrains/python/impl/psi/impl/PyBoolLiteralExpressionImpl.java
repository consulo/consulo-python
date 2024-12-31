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

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class PyBoolLiteralExpressionImpl extends PyElementImpl implements PyBoolLiteralExpression {
  public PyBoolLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
    return PyBuiltinCache.getInstance(this).getBoolType();
  }

  @Override
  public boolean getValue() {
    return "True".equals(getText());
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBoolLiteralExpression(this);
  }
}
