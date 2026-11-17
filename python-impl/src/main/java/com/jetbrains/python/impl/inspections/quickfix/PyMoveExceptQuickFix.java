// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.localize.LocalizeValue;
import com.google.common.collect.Lists;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.ArrayList;

public class PyMoveExceptQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMoveExceptUp();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyExceptPart part = PsiTreeUtil.getParentOfType(element, PyExceptPart.class);
        if (part == null) {
            return;
        }
        PyExpression exceptClassExpression = part.getExceptClass();
        if (exceptClassExpression == null) {
            return;
        }

        var resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(project));
        PsiElement exceptClass = ((PyReferenceExpression)exceptClassExpression).followAssignmentsChain(resolveContext).getElement();
        if (exceptClass instanceof PyClass) {
            PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(part, PyTryExceptStatement.class);
            if (statement == null) {
                return;
            }

            PyExceptPart prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(part, PyExceptPart.class);
            ArrayList<PyClass> superClasses = Lists.newArrayList(((PyClass)exceptClass).getSuperClasses(null));
            while (prevExceptPart != null) {
                PyExpression classExpression = prevExceptPart.getExceptClass();
                if (classExpression == null) {
                    return;
                }
                PsiElement aClass = ((PyReferenceExpression)classExpression).followAssignmentsChain(resolveContext).getElement();
                if (aClass instanceof PyClass) {
                    if (superClasses.contains(aClass)) {
                        statement.addBefore(part, prevExceptPart);
                        part.delete();
                        return;
                    }
                }
                prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(prevExceptPart, PyExceptPart.class);
            }
        }
    }
}
