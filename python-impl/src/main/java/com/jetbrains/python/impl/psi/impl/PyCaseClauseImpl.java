// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public class PyCaseClauseImpl extends PyElementImpl implements PyCaseClause, PyCaptureContext {
  public PyCaseClauseImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCaseClause(this);
  }

  // TODO consulo: in JB this is the default method of PyAstStatementPart (python-ast layer not ported)
  @Override
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @Override
  public @Nullable PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context) {
    return getSubjectTypeBefore(context);
  }

  private @Nullable PyType getSubjectTypeBefore(TypeEvalContext context) {
    PyCaseClause prevClause = PsiTreeUtil.getPrevSiblingOfType(this, PyCaseClause.class);
    if (prevClause != null) {
      return prevClause.getSubjectTypeAfter(context);
    }
    else {
      if (!(getParent() instanceof PyMatchStatement matchStatement)) {
        return null;
      }
      PyExpression subject = matchStatement.getSubject();
      if (subject == null) {
        return null;
      }
      return context.getType(subject);
    }
  }

  @Override
  public @Nullable PyType getSubjectTypeAfter(TypeEvalContext context) {
    // TODO consulo: port JB body (narrowing of the subject type by the case pattern). Missing in Consulo:
    //  PyNeverType.NEVER, PyEvaluator.evaluateAsBoolean(PyExpression, boolean),
    //  public PyTypeAssertionEvaluator.createAssertionType(PyType, PyType, boolean, boolean, TypeEvalContext).
    //  Until then the subject type is not narrowed (unknown)
    return null;
  }
}
