// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Forward references resolution for annotations and pyi stubs.
 *
 * @see <a href="PEP-484">https://www.python.org/dev/peps/pep-0484/</a>
 * @see <a href="PEP-563">https://www.python.org/dev/peps/pep-0563/</a>
 */
@ExtensionImpl
public final class PyForwardReferenceResolveProvider implements PyReferenceResolveProvider {

  @Override
  public List<RatedResolveResult> resolveName(PyQualifiedExpression element, TypeEvalContext context) {
    if (!PyResolveUtil.allowForwardReferences(element)) {
      return Collections.emptyList();
    }

    final String referencedName = element.getReferencedName();
    if (referencedName == null) {
      return Collections.emptyList();
    }
    final ScopeOwner originalOwner = ScopeUtil.getScopeOwner(element);

    if (originalOwner != null) {
      List<RatedResolveResult> result = new ArrayList<>();
      for (PsiElement it : PyResolveUtil.resolveLocally(originalOwner, referencedName)) {
        result.add(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, it));
      }
      return result;
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  public boolean allowsForwardOutgoingReferencesInClass(PyQualifiedExpression element) {
    return PyResolveUtil.allowForwardReferences(element);
  }
}
