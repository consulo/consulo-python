// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyKeywordPatternImpl extends PyElementImpl implements PyKeywordPattern {
  public PyKeywordPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordPattern(this);
  }

  // TODO consulo: com.jetbrains.python.impl.psi.impl.references.PyKeywordPatternReference is not ported
  //  (requires com.jetbrains.python.PythonCodeStyleService), JB overrides:
  //  public PsiReference getReference() {
  //    return new PyKeywordPatternReference(this);
  //  }

  @Override
  public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    PyPattern valuePattern = getValuePattern();
    return valuePattern != null ? context.getType(valuePattern) : null;
  }
}
