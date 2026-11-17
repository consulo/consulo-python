// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.dataflow.scope;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtilCore;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.impl.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyListCompExpressionNavigator;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public final class ScopeUtil {
    private ScopeUtil() {
    }

    public static @Nullable PsiElement getParameterScope(@Nullable PsiElement element) {
        if (element instanceof PyNamedParameter) {
            PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
            if (function != null) {
                return function;
            }
        }

        PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
        if (exceptPart != null) {
            return exceptPart;
        }

        PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
        if (forStatement != null) {
            return forStatement;
        }

        PyListCompExpression listCompExpression = PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element);
        if (listCompExpression != null) {
            return listCompExpression;
        }
        return null;
    }

    /**
     * Return the scope owner for the element. This also applies for elements of instance {@code AstScopeOwner}.
     * <br>
     * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
     * <br>
     * This method does not access AST if underlying PSI is stub based.
     */
    public static @Nullable ScopeOwner getScopeOwner(@Nullable PsiElement element) {
        return (ScopeOwner) ScopeUtilCore.getScopeOwner(element);
    }

    public static @Nullable ScopeOwner getDeclarationScopeOwner(@Nullable PsiElement anchor, @Nullable String name) {
        if (name != null) {
            ScopeOwner originalScopeOwner = getScopeOwner(anchor);
            ScopeOwner scopeOwner = originalScopeOwner;
            while (scopeOwner != null) {
                if (!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner) {
                    Scope scope = ControlFlowCache.getScope(scopeOwner);
                    if (scope.containsDeclaration(name)) {
                        return scopeOwner;
                    }
                }
                scopeOwner = getScopeOwner(scopeOwner);
            }
        }
        return null;
    }

    /**
     * Kotlin {@code Sequence<PsiElement>} in JB; a lazy {@link Iterable} here.
     */
    public static Iterable<PsiElement> getElementsOfAccessType(String name,
                                                               ScopeOwner scopeOwner,
                                                               ReadWriteInstruction.ACCESS type) {
        return () -> Arrays.stream(ControlFlowCache.getControlFlow(scopeOwner).getInstructions())
            .filter(ReadWriteInstruction.class::isInstance)
            .map(ReadWriteInstruction.class::cast)
            .filter(it -> name.equals(it.getName()) && type == it.getAccess())
            .map(ReadWriteInstruction::getElement)
            .filter(Objects::nonNull)
            .map(it -> (PsiElement) it)
            .iterator();
    }
}
