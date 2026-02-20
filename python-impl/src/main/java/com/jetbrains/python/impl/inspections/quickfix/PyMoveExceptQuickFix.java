/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections.quickfix;

import com.google.common.collect.Lists;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;

public class PyMoveExceptQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMoveExceptUp();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyExceptPart part = PsiTreeUtil.getParentOfType(element, PyExceptPart.class);
        if (part == null) {
            return;
        }
        PyExpression exceptClassExpression = part.getExceptClass();
        if (exceptClassExpression == null) {
            return;
        }

        PsiElement exceptClass =
            ((PyReferenceExpression) exceptClassExpression).followAssignmentsChain(PyResolveContext.noImplicits()).getElement();
        if (exceptClass instanceof PyClass) {
            PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(part, PyTryExceptStatement.class);
            if (statement == null) {
                return;
            }

            PyExceptPart prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(part, PyExceptPart.class);
            ArrayList<PyClass> superClasses = Lists.newArrayList(((PyClass) exceptClass).getSuperClasses(null));
            while (prevExceptPart != null) {
                PyExpression classExpression = prevExceptPart.getExceptClass();
                if (classExpression == null) {
                    return;
                }
                PsiElement aClass =
                    ((PyReferenceExpression) classExpression).followAssignmentsChain(PyResolveContext.noImplicits()).getElement();
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
