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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;

import java.util.Collections;
import java.util.List;

/**
 * @author ktisha
 */
public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMakeFunction();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (problemFunction == null) {
            return;
        }
        PyClass containingClass = problemFunction.getContainingClass();
        if (containingClass == null) {
            return;
        }

        List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);
        PyParameter[] parameters = problemFunction.getParameterList().getParameters();
        if (parameters.length > 0) {
            parameters[0].delete();
        }

        PsiElement copy = problemFunction.copy();
        problemFunction.delete();
        PsiElement parent = containingClass.getParent();
        PyClass aClass = PsiTreeUtil.getTopmostParentOfType(containingClass, PyClass.class);
        if (aClass == null) {
            aClass = containingClass;
        }
        copy = parent.addBefore(copy, aClass);

        for (UsageInfo usage : usages) {
            if (usage.getElement() instanceof PyReferenceExpression usageRefExpr) {
                PsiFile usageFile = usageRefExpr.getContainingFile();
                updateUsage(copy, usageRefExpr, usageFile, !usageFile.equals(parent));
            }
        }
    }

    @RequiredWriteAction
    private static void updateUsage(
        PsiElement finalElement,
        PyReferenceExpression element,
        PsiFile usageFile,
        boolean addImport
    ) {
        PyExpression qualifier = element.getQualifier();
        if (qualifier == null) {
            return;
        }
        if (qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
            PyUtil.removeQualifier(element);
            return;
        }
        if (qualifier instanceof PyCallExpression) {              // remove qualifier A().m()
            if (addImport) {
                AddImportHelper.addImport((PsiNamedElement) finalElement, usageFile, element);
            }

            PyUtil.removeQualifier(element);
            removeFormerImport(usageFile, addImport);
        }
        else {
            PsiReference reference = qualifier.getReference();
            if (reference == null) {
                return;
            }

            PsiElement resolved = reference.resolve();
            if (resolved instanceof PyTargetExpression) {  // qualifier came from assignment  a = A(); a.m()
                updateAssignment(element, resolved);
            }
            else if (resolved instanceof PyClass) {     //call with first instance argument A.m(A())
                PyUtil.removeQualifier(element);
                updateArgumentList(element);
            }
        }
    }

    @RequiredReadAction
    private static void removeFormerImport(PsiFile usageFile, boolean addImport) {
        if (usageFile instanceof PyFile && addImport) {
            LocalInspectionToolSession session = new LocalInspectionToolSession(usageFile, 0, usageFile.getTextLength());
            final PyUnresolvedReferencesInspection.Visitor visitor =
                new PyUnresolvedReferencesInspection.Visitor(null, session, Collections.<String>emptyList());
            usageFile.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyElement(PyElement node) {
                    super.visitPyElement(node);
                    node.accept(visitor);
                }
            });

            visitor.optimizeImports();
        }
    }

    @RequiredWriteAction
    private static void updateAssignment(PyReferenceExpression element, PsiElement resolved) {
        if (resolved.getParent() instanceof PyAssignmentStatement assignment
            && assignment.getAssignedValue() instanceof PyCallExpression assignedCall
            && assignedCall.getCallee() instanceof PyReferenceExpression calleeRefExpr) {
            PyExpression calleeQualifier = calleeRefExpr.getQualifier();
            if (calleeQualifier != null) {
                assignedCall.replace(calleeQualifier);
            }
            else {
                PyUtil.removeQualifier(element);
            }
        }
    }

    @RequiredWriteAction
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
}
