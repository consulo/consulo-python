// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.application.WriteAction;
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
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix {
    public PyMakeFunctionFromMethodQuickFix() {
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMakeFunction();
    }

    @Override
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

        List<PyReferenceExpression> usages = StreamEx.of(PyPsiIndexUtil.findUsages(problemFunction, false))
            .map(UsageInfo::getElement)
            .select(PyReferenceExpression.class)
            .toList();

        // TODO consulo: ApplicationManagerEx.runWriteActionWithCancellableProgressInDispatchThread is not exported to plugins,
        //  JB runs this under the PyLocalize.refactoringProgressTitleUpdatingExistingUsages() cancellable progress
        WriteAction.run(() -> {
            PyFunction function = transformDefinition(problemFunction);
            for (int i = 0; i < usages.size(); i++) {
                PyReferenceExpression usage = usages.get(i);
                PsiFile usageFile = usage.getContainingFile();
                updateUsage(function, usage, usageFile, !usageFile.equals(containingClass.getContainingFile()));
            }
        });
    }

    private static PyFunction transformDefinition(PyFunction method) {
        PyParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0) {
            parameters[0].delete();
        }
        PyClass topmostClass = PsiTreeUtil.getTopmostParentOfType(method, PyClass.class);
        assert topmostClass != null;
        PsiElement copy = method.copy();
        method.delete();
        return (PyFunction)topmostClass.getParent().addBefore(copy, topmostClass);
    }

    private static void updateUsage(PsiElement finalElement, PyReferenceExpression element,
            PsiFile usageFile, boolean addImport) {
        PyExpression qualifier = element.getQualifier();
        if (qualifier == null) {
            return;
        }
        if (qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
            PyUtil.removeQualifier(element);
            return;
        }
        if (qualifier instanceof PyCallExpression) {              // remove qualifier A().m()
            if (addImport)
                AddImportHelper.addImport((PsiNamedElement)finalElement, usageFile, element);

            if (element.isValid()) {
                PyUtil.removeQualifier(element);
            }
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

    private static void removeFormerImport(PsiFile usageFile, boolean addImport) {
        if (usageFile instanceof PyFile && addImport) {
            PyClassRefactoringUtil.optimizeImports(usageFile);
        }
    }

    private static void updateAssignment(PyReferenceExpression element, PsiElement resolved) {
        PsiElement parent = resolved.getParent();
        if (parent instanceof PyAssignmentStatement) {
            PyExpression value = ((PyAssignmentStatement)parent).getAssignedValue();
            if (value instanceof PyCallExpression) {
                PyExpression callee = ((PyCallExpression)value).getCallee();
                if (callee instanceof PyReferenceExpression) {
                    PyExpression calleeQualifier = ((PyReferenceExpression)callee).getQualifier();
                    if (calleeQualifier != null) {
                        value.replace(calleeQualifier);
                    }
                    else {
                        PyUtil.removeQualifier(element);
                    }
                }
            }
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
