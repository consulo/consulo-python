// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class RedundantParenthesesQuickFix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(RedundantParenthesesQuickFix.class);

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRedundantParentheses();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyParenthesizedExpression) {
            PsiElement binaryExpression = ((PyParenthesizedExpression)element).getContainedExpression();
            PyBinaryExpression parent = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
            if (binaryExpression instanceof PyBinaryExpression && parent != null) {
                if (!replaceBinaryExpression((PyBinaryExpression)binaryExpression)) {
                    element.replace(binaryExpression);
                }
            }
            else {
                PyExpression content = PyPsiUtils.flattenParens((PyParenthesizedExpression)element);
                if (content != null) {
                    element.replace(content);
                }
            }
        }
        else if (element instanceof PyArgumentList) {
            LOG.assertTrue(element.getParent() instanceof PyClass, "Parent type: " + element.getParent().getClass());
            LOG.assertTrue(((PyArgumentList)element).getArguments().length == 0, "Argument list: " + element.getText());
            ASTNode nameNode = PyElementGenerator.getInstance(project).createFromText(
                LanguageLevel.forElement(element), PyClass.class, "class A: pass").getNameNode();
            if (nameNode != null) {
                PsiElement emptyArgList = nameNode.getPsi().getNextSibling();
                element.replace(emptyArgList);
            }
            else {
                element.delete();
            }
        }
    }

    private static boolean replaceBinaryExpression(PyBinaryExpression element) {
        PyExpression left = element.getLeftExpression();
        PyExpression right = element.getRightExpression();
        if (left instanceof PyParenthesizedExpression &&
                right instanceof PyParenthesizedExpression) {
            PyExpression leftContained = ((PyParenthesizedExpression)left).getContainedExpression();
            PyExpression rightContained = ((PyParenthesizedExpression)right).getContainedExpression();
            if (leftContained != null && rightContained != null &&
                    !(leftContained instanceof PyTupleExpression) && !(rightContained instanceof PyTupleExpression)) {
                left.replace(leftContained);
                right.replace(rightContained);
                return true;
            }
        }
        return false;
    }
}
