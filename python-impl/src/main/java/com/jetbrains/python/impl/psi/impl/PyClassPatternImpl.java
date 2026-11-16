// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

// TODO consulo: the type evaluation of JB PyClassPatternImpl is not ported: getType, canExcludePatternType,
//  getCaptureTypeForChild and the static helpers isExhaustive, getMatchArgs, getMemberType. Missing in Consulo:
//  PyAnyType, PyNeverType, PyTypeMember, PyType.findMember(String, PyResolveContext),
//  PyTypingTypeProvider.parameterizeType(PyType, TypeEvalContext),
//  public PyTypeAssertionEvaluator.createAssertionType(PyType, PyType, boolean, boolean, TypeEvalContext),
//  PyTypeUtil.toStream / toUnion / extractStringLiteralsFromTupleType, PyClass.getOwnMatchArgs(),
//  PyTypeChecker.unifyReceiver(PyType, TypeEvalContext)
public class PyClassPatternImpl extends PyElementImpl implements PyClassPattern, PyCaptureContext {
  public PyClassPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClassPattern(this);
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
