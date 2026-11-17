// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiNamedElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator expression PSI.
 */
public interface PyAstGeneratorExpression extends PyAstComprehensionElement {
    @Override
    @RequiredReadAction
    default List<PsiNamedElement> getNamedElements() {
        // extract whatever names are defined in "for" components
        List<? extends PyComprehensionForComponent> fors = getForComponents();
        PyAstExpression[] for_targets = new PyAstExpression[fors.size()];
        int i = 0;
        for (PyComprehensionForComponent for_comp : fors) {
            for_targets[i] = for_comp.getIteratorVariable();
            i += 1;
        }
        List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndStars(for_targets);
        List<PsiNamedElement> results = new ArrayList<>();
        for (PyAstExpression expression : expressions) {
            if (expression instanceof PsiNamedElement) {
                results.add((PsiNamedElement)expression);
            }
        }
        return results;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyGeneratorExpression(this);
    }
}
