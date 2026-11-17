// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * @author Alexey.Ivanov
 */
public class ReplaceExceptPartQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.intnConvertExceptTo();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement exceptPart = descriptor.getPsiElement();
        if (exceptPart instanceof PyExceptPart) {
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            PsiElement element = ((PyExceptPart) exceptPart).getExceptClass().getNextSibling();
            while (element instanceof PsiWhiteSpace) {
                element = element.getNextSibling();
            }
            assert element != null;
            PyTryExceptStatement newElement =
                elementGenerator.createFromText(
                    LanguageLevel.forElement(exceptPart),
                    PyTryExceptStatement.class,
                    "try:  pass except a as b:  pass"
                );
            ASTNode node = newElement.getExceptParts()[0].getNode().findChildByType(PyTokenTypes.AS_KEYWORD);
            assert node != null;
            element.replace(node.getPsi());
        }
    }
}
