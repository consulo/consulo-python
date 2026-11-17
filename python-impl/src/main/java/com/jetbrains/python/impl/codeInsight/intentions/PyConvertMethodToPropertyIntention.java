// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;

import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertMethodToPropertyIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertMethodToPropertyIntention extends PyBaseIntentionAction {

    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertMethodToProperty();
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
        if (function.getParameterList().getParameters().length > 1) {
            return false;
        }
        PyDecoratorList decoratorList = function.getDecoratorList();
        if (decoratorList != null) {
            return false;
        }
        boolean[] available = {false};
        function.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReturnStatement(PyReturnStatement node) {
                if (node.getExpression() != null)
                    available[0] = true;
            }

            @Override
            public void visitPyYieldExpression(PyYieldExpression node) {
                available[0] = true;
            }
        });

        return available[0];
    }

    @Override
    public boolean startInWriteAction() {
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

        if (!prepareForWrite(file, usages)) {

            return;

        }
        WriteAction.run(() -> {
            PyUtil.addDecorator(problemFunction, "@" + PyNames.PROPERTY);
            deleteUsages(usages);
        });
    }

    private static boolean prepareForWrite(PsiFile file, List<UsageInfo> usages) {
        List<PsiElement> toWrite = ContainerUtil.prepend(ContainerUtil.mapNotNull(usages, UsageInfo::getElement), file);
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(toWrite)) {
            return false;
        }
        return true;
    }

    private static void deleteUsages(List<UsageInfo> usages) {
        for (UsageInfo usage : usages) {
            PsiElement usageElement = usage.getElement();
            if (usageElement instanceof PyReferenceExpression) {
                PsiElement parent = usageElement.getParent();
                if (parent instanceof PyCallExpression callExpression) {
                    convertCallExpToRefExpr(callExpression);
                }
            }
        }
    }

    private static void convertCallExpToRefExpr(PyCallExpression callExpression) {
        PyExpression callee = callExpression.getCallee();
        if (callee == null) {
            throw new PsiInvalidElementAccessException(callExpression);
        }

        String newReferenceExpressionText = callee.getText();

        PyExpression newExpression = PyElementGenerator.getInstance(callExpression.getProject())
            .createExpressionFromText(LanguageLevel.forElement(callExpression), newReferenceExpressionText);

        if (newExpression instanceof PyReferenceExpression referenceExpression) {
            callExpression.replace(referenceExpression);
        }
        else {
            throw new IllegalStateException(
                    "Failed to create reference expression from call expression with text: \"" + callExpression.getText() + "\"");
        }
    }
}

