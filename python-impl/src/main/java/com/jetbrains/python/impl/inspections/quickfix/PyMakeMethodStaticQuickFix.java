// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.application.WriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PyMakeMethodStaticQuickFix implements LocalQuickFix {
    public PyMakeMethodStaticQuickFix() {
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMakeStatic();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (problemFunction == null) {
            return;
        }

        List<PyReferenceExpression> usages = StreamEx.of(PyPsiIndexUtil.findUsages(problemFunction, false))
            .map(UsageInfo::getElement)
            .select(PyReferenceExpression.class)
            .toList();

        // TODO consulo: ApplicationManagerEx.runWriteActionWithCancellableProgressInDispatchThread is not exported to plugins,
        //  JB runs this under the PyLocalize.refactoringProgressTitleUpdatingExistingUsages() cancellable progress
        WriteAction.run(() -> {
            updateDefinition(problemFunction);
            for (int i = 0; i < usages.size(); i++) {
                updateUsage(usages.get(i));
            }
        });
    }

    private static void updateDefinition(PyFunction function) {
        PyParameter[] parameters = function.getParameterList().getParameters();
        if (parameters.length > 0) {
            parameters[0].delete();
        }
        PyUtil.addDecorator(function, "@" + PyNames.STATICMETHOD);
    }

    private static void updateUsage(PyReferenceExpression element) {
        PyExpression qualifier = element.getQualifier();
        if (qualifier == null) {
            return;
        }
        PsiReference reference = qualifier.getReference();
        if (reference == null) {
            return;
        }
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PyClass) {     //call with first instance argument A.m(A())
            updateArgumentList(element);
        }
    }

    private static void updateArgumentList(PyReferenceExpression element) {
        PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
        if (callExpression == null) {
            return;
        }
        PyArgumentList argumentList = callExpression.getArgumentList();
        if (argumentList == null) {
            return;
        }
        PyExpression[] arguments = argumentList.getArguments();
        if (arguments.length > 0) {
            arguments[0].delete();
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(PsiFile currentFile) {
        return currentFile;
    }
}
