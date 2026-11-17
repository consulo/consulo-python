// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.editor.PyEditorHandlerConfig;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.Stack;

/**
 * User: catherine
 * <p>
 * QuickFix to remove all unnecessary backslashes in expression
 */
public class RemoveUnnecessaryBackslashQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRemoveUnnecessaryBackslash();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiElement parent = PsiTreeUtil.getParentOfType(element, PyEditorHandlerConfig.IMPLICIT_WRAP_CLASSES);
        removeBackSlash(parent);
    }

    public static void removeBackSlash(PsiElement parent) {
        if (parent != null) {
            Stack<PsiElement> stack = new Stack<>();
            if (parent instanceof PyParenthesizedExpression) {
                stack.push(((PyParenthesizedExpression)parent).getContainedExpression());
            }
            else {
                stack.push(parent);
            }
            while (!stack.isEmpty()) {
                PsiElement el = stack.pop();
                PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(el, PsiWhiteSpace.class);
                if (children != null) {
                    for (PsiWhiteSpace ws : children) {
                        if (ws.getText().contains("\\")) {
                            ws.delete();
                        }
                    }
                }
                for (PsiElement psiElement : el.getChildren()) {
                    stack.push(psiElement);
                }
            }
        }
    }
}
