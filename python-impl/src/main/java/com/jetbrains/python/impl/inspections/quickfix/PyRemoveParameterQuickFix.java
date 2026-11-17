// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import one.util.streamex.StreamEx;

public class PyRemoveParameterQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveParameter();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyParameter psi = PyUtil.as(descriptor.getPsiElement(), PyParameter.class);
        assert psi != null;
        PyCallableParameter parameter = PyCallableParameterImpl.psi(psi);
        TypeEvalContext ctx = TypeEvalContext.codeAnalysis(project, psi.getContainingFile());

        PyFunction function = PsiTreeUtil.getParentOfType(psi, PyFunction.class);
        if (function != null) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(ctx);

            StreamEx
                .of(PyPsiIndexUtil.findUsages(function, false))
                .map(UsageInfo::getElement)
                .nonNull()
                .map(PsiElement::getParent)
                .select(PyCallExpression.class)
                .flatCollection(callExpression -> callExpression.multiMapArguments(resolveContext))
                .flatCollection(mapping -> mapping.getMappedParameters().entrySet())
                .filter(entry -> parameter.equals(entry.getValue()))
                .forEach(entry -> entry.getKey().delete());

            PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
            String parameterName = parameter.getName();
            if (docStringExpression != null && parameterName != null) {
                PyDocstringGenerator.forDocStringOwner(function).withoutParam(parameterName).buildAndInsert();
            }

            if (parameterName != null) {
                StreamEx
                    .of(PyiUtil.getOverloads(function, ctx))
                    .map(overload -> overload.getParameterList().getParameters())
                    .map(parameters -> ContainerUtil.find(parameters, overloadParameter -> parameterName.equals(overloadParameter.getName())))
                    .nonNull()
                    .forEach(PsiElement::delete);
            }
        }

        psi.delete();
    }
}
