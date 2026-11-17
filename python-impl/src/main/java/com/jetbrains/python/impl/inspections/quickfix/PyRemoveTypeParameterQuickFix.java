// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * Removes a single type parameter from a type parameter list.
 * If the list becomes empty, removes the brackets as well.
 */
// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyRemoveTypeParameterQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveTypeParameter();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyTypeParameter typeParameter = PsiTreeUtil.getParentOfType(element, PyTypeParameter.class, false);
        if (typeParameter == null) {
            return;
        }
        PyTypeParameterList list = PsiTreeUtil.getParentOfType(typeParameter, PyTypeParameterList.class, false);
        if (list == null) {
            return;
        }

        PsiElement prev = typeParameter.getPrevSibling();
        PsiElement next = typeParameter.getNextSibling();

        if (prev instanceof PsiWhiteSpace) {
            prev = prev.getPrevSibling();
        }
        if (next instanceof PsiWhiteSpace) {
            next = next.getNextSibling();
        }

        if (prev != null && prev.getNode().getElementType() == PyTokenTypes.COMMA) {
            prev.delete();
            PsiElement afterComma = typeParameter.getPrevSibling();
            if (afterComma instanceof PsiWhiteSpace) {
                afterComma.delete();
            }
        }
        else if (next != null && next.getNode().getElementType() == PyTokenTypes.COMMA) {
            PsiElement ws = next.getNextSibling();
            next.delete();
            if (ws instanceof PsiWhiteSpace) {
                ws.delete();
            }
        }

        typeParameter.delete();

        if (list.getTypeParameters().isEmpty()) {
            ((PyElement)list).delete();
        }
    }
}
