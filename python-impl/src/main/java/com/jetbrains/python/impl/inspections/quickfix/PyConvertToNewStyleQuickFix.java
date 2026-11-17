// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyConvertToNewStyleQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixConvertToNewStyle();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        assert pyClass != null;

        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyArgumentList expressionList = pyClass.getSuperClassExpressionList();
        if (expressionList != null) {
            PyExpression object = generator.createExpressionFromText(LanguageLevel.forElement(element), "object");
            expressionList.addArgumentFirst(object);
        }
        else {
            PyArgumentList list =
                generator.createFromText(LanguageLevel.forElement(element), PyClass.class, "class A(object):pass").getSuperClassExpressionList();
            assert list != null;
            ASTNode node = pyClass.getNameNode();
            assert node != null;
            PsiElement oldArgList = node.getPsi().getNextSibling();
            if (oldArgList instanceof PyArgumentList) {
                oldArgList.replace(list);
            }
        }
    }
}
