// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel;

import com.google.common.collect.Lists;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeLocalFunctionTopLevelProcessor extends PyBaseMakeFunctionTopLevelProcessor {
    public PyMakeLocalFunctionTopLevelProcessor(PyFunction targetFunction, String destination) {
        super(targetFunction, destination);
    }

    @Override
    protected LocalizeValue getRefactoringName() {
        return PyLocalize.refactoringMakeLocalFunctionTopLevelDialogTitle();
    }

    @Override
    protected void updateUsages(Collection<String> newParamNames, UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element != null) {
                PyCallExpression parentCall = as(element.getParent(), PyCallExpression.class);
                if (parentCall != null && parentCall.getArgumentList() != null) {
                    addArguments(parentCall.getArgumentList(), newParamNames);
                }
            }
        }
    }

    @Override
    protected PyFunction createNewFunction(Collection<String> newParamNames) {
        PyFunction copied = (PyFunction) myFunction.copy();
        addParameters(copied.getParameterList(), newParamNames);
        return copied;
    }

    @Override
    protected List<String> collectNewParameterNames() {
        Set<String> enclosingScopeReads = new LinkedHashSet<>();
        for (ScopeOwner owner : PsiTreeUtil.collectElementsOfType(myFunction, ScopeOwner.class)) {
            AnalysisResult result = analyseScope(owner);
            if (!result.nonlocalWritesToEnclosingScope.isEmpty()) {
                throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorNonlocalWrites());
            }
            if (!result.readsOfSelfParametersFromEnclosingScope.isEmpty()) {
                throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorSelfReads());
            }
            for (PsiElement element : result.readsFromEnclosingScope) {
                if (element instanceof PyElement pyElement) {
                    ContainerUtil.addIfNotNull(enclosingScopeReads, pyElement.getName());
                }
            }
        }
        return Lists.newArrayList(enclosingScopeReads);
    }

    @Override
    protected String getRefactoringId() {
        return "refactoring.python.make.local.function.top.level";
    }
}
