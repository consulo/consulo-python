// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyAddExceptionSuperClassQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameAddExceptionBase();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyCallExpression) {
            PyExpression callee = ((PyCallExpression)element).getCallee();
            if (callee instanceof PyReferenceExpression) {
                PsiPolyVariantReference reference = ((PyReferenceExpression)callee).getReference();
                PsiElement psiElement = reference.resolve();
                if (psiElement instanceof PyClass) {
                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    PyArgumentList list = ((PyClass)psiElement).getSuperClassExpressionList();
                    if (list != null) {
                        PyExpression exception =
                            generator.createExpressionFromText(LanguageLevel.forElement(element), "Exception");
                        list.addArgument(exception);
                    }
                    else {
                        PyArgumentList expressionList = generator.createFromText(
                            LanguageLevel.forElement(element), PyClass.class, "class A(Exception): pass").getSuperClassExpressionList();
                        assert expressionList != null;
                        ASTNode nameNode = ((PyClass)psiElement).getNameNode();
                        assert nameNode != null;
                        PsiElement oldArgList = nameNode.getPsi().getNextSibling();
                        oldArgList.replace(expressionList);
                    }
                }
            }
        }
    }
}
