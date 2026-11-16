// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyDoubleStarPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyMappingPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;


// TODO consulo: JB also implements com.intellij.psi.PsiListLikeElement (missing in Consulo platform)
// TODO consulo: the type evaluation of JB PyMappingPatternImpl is not ported: getType, getCaptureTypeForChild,
//  wrapInMappingType and the file-level helpers (getValueType, getKeyString). Missing in Consulo:
//  PyAnyType, PyNeverType, PyLiteralType, PyTypedDictType, PyUnionType.unionOrUnknown,
//  PyTypeUtil.compositeComponents / convertToType / widenLiteralAndNumeric
public class PyMappingPatternImpl extends PyElementImpl implements PyMappingPattern, PyCaptureContext {
  public PyMappingPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  private List<PyPattern> getElements() {
    return Arrays.asList(findChildrenByClass(PyPattern.class));
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyMappingPattern(this);
  }

  public List<PyPattern> getComponents() {
    return getElements();
  }

  @Override
  public boolean canExcludePatternType(TypeEvalContext context) {
    List<PyPattern> elements = getElements();
    return elements.size() == 1 && elements.get(0) instanceof PyDoubleStarPattern;
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
