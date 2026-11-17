// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.dataflow.scope;

import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.dataFlow.DFALimitExceededException;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface Scope {
    /**
     * @return the local, instance or class variable or the parameter with the given name, from the reaching definitions
     * @deprecated The method uses the obsolete DFA analysis. This interface has no replacement for it.
     */
    @Nullable
    @Deprecated
    ScopeVariable getDeclaredVariable(PsiElement anchorElement,
                                      String name,
                                      TypeEvalContext typeEvalContext) throws DFALimitExceededException;

    boolean hasGlobals();

    boolean isGlobal(String name);

    boolean hasNonLocals();

    boolean isNonlocal(String name);

    boolean containsDeclaration(String name);

    List<PyImportedNameDefiner> getImportedNameDefiners();

    Collection<PsiNamedElement> getNamedElements(String name, boolean includeNestedGlobals);

    Collection<PsiNamedElement> getNamedElements();

    Collection<PyTargetExpression> getTargetExpressions();

    Set<String> getGlobals();
}
