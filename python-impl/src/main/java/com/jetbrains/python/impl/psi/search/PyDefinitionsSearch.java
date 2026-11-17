// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.search.DefinitionsScopedSearch;
import consulo.language.psi.search.DefinitionsScopedSearchExecutor;

import java.util.function.Predicate;


@ExtensionImpl
public final class PyDefinitionsSearch implements DefinitionsScopedSearchExecutor {
    @Override
    public boolean execute(DefinitionsScopedSearch.SearchParameters parameters, Predicate<? super PsiElement> consumer) {
        PsiElement element = parameters.getElement();
        if (element instanceof PyElement) {
            PsiElement finalElement = element;
            boolean isInsideStub = ReadAction.compute(() -> PyiUtil.isInsideStub(finalElement));
            if (isInsideStub) {
                var originalElement = ReadAction.compute(() -> PyiUtil.getOriginalElement((PyElement)finalElement));
                if (originalElement != null) {
                    element = originalElement;
                    if (!consumer.test(element)) {
                        return false;
                    }
                }
            }
        }
        if (element instanceof PyClass) {
            return processInheritors((PyClass)element, consumer);
        }
        else if (element instanceof PyFunction) {
            return processOverridingMethods((PyFunction)element, consumer);
        }
        else if (element instanceof PyTargetExpression) {
            return processAssignmentStatement(element, consumer);
        }
        return true;
    }

    private static boolean processInheritors(PyClass pyClass, Predicate<? super PsiElement> consumer) {
        return ReadAction.compute(() -> PyClassInheritorsSearch.search(pyClass, true)).forEach(consumer::test);
    }

    private static boolean processOverridingMethods(PyFunction pyFunction,
                                                    Predicate<? super PsiElement> consumer) {
        return ReadAction.compute(() -> PyOverridingMethodsSearch.search(pyFunction, true)).forEach(consumer::test);
    }

    private static boolean processAssignmentStatement(PsiElement element,
                                                      Predicate<? super PsiElement> consumer) {
        PsiElement parent = ReadAction.compute(() -> element.getParent());
        return !(parent instanceof PyAssignmentStatement) || consumer.test(parent);
    }
}
