// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;

import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertStaticMethodToFunctionIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertStaticMethodToFunctionIntention extends PyBaseIntentionAction {

    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertStaticMethodToFunction();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile)) {
            return false;
        }
        PsiElement element = PyUtil.findNonWhitespaceAtOffset(psiFile, editor.getCaretModel().getOffset());
        PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (function == null) {
            return false;
        }
        PyClass containingClass = function.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PyDecoratorList decoratorList = function.getDecoratorList();
        if (decoratorList != null) {
            PyDecorator staticMethod = decoratorList.findDecorator(PyNames.STATICMETHOD);
            if (staticMethod != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (problemFunction == null) {
            return;
        }
        PyClass containingClass = problemFunction.getContainingClass();
        if (containingClass == null) {
            return;
        }
        List<UsageInfo> usages = PyPsiIndexUtil.findUsages(problemFunction, false);
        PyDecoratorList decoratorList = problemFunction.getDecoratorList();
        if (decoratorList != null) {
            PyDecorator decorator = decoratorList.findDecorator(PyNames.STATICMETHOD);
            if (decorator != null) {
                decorator.delete();
            }
        }

        PsiElement copy = problemFunction.copy();
        problemFunction.delete();
        file.addAfter(copy, containingClass);

        for (UsageInfo usage : usages) {
            PsiElement usageElement = usage.getElement();
            if (usageElement instanceof PyReferenceExpression) {
                PyUtil.removeQualifier((PyReferenceExpression)usageElement);
            }
        }
    }
}

