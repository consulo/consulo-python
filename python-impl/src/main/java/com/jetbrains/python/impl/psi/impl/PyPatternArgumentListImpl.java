// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPatternArgumentList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;

import java.util.List;

// TODO consulo: JB also implements com.intellij.psi.PsiListLikeElement (missing in Consulo platform)
public class PyPatternArgumentListImpl extends PyElementImpl implements PyPatternArgumentList {
  public PyPatternArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPatternArgumentList(this);
  }

  public List<? extends PsiElement> getComponents() {
    return getPatterns();
  }

  @Override
  @RequiredWriteAction
  public void deleteChildInternal(ASTNode child) {
    if (getPatterns().contains(child.getPsi())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }
}
