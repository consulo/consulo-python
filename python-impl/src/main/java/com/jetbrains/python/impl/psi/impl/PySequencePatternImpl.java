// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PySequencePattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

import java.util.List;

// TODO consulo: JB also implements com.intellij.psi.PsiListLikeElement (missing in Consulo platform)
// TODO consulo: the type evaluation of JB PySequencePatternImpl is not ported: getType, canExcludePatternType,
//  getCaptureTypeForChild, getElementTypeSkippingStar, getSequenceCaptureType, wrapInListType, wrapInSequenceType
//  and the file-level helpers (getCapturedTypesFromSequenceType, isHeterogeneousTuple, takeIfSizeMatches,
//  expandVariadics, intersect). Missing in Consulo: PyNeverType, PyUnpackedTupleType, PyUnionType.unionOrUnknown,
//  PyTypeUtil.compositeComponents / convertToType / widenLiteralAndNumeric, PyClassPatternImpl.isExhaustive
public class PySequencePatternImpl extends PyElementImpl implements PySequencePattern, PyCaptureContext {
  public PySequencePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySequencePattern(this);
  }

  public List<PyPattern> getComponents() {
    return getElements();
  }

  @Override
  public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    // TODO consulo: not ported, see class comment
    return null;
  }

  @Override
  public @Nullable PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context) {
    // TODO consulo: not ported, see class comment
    return null;
  }
}
