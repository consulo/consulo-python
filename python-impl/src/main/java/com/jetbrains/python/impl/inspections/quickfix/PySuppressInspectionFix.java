// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyElement;
import consulo.language.editor.inspection.AbstractBatchSuppressByNoInspectionCommentFix;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;

/**
 * @author yole
 */
public class PySuppressInspectionFix extends AbstractBatchSuppressByNoInspectionCommentFix {
    private final Class<? extends PyElement> myContainerClass;

    public PySuppressInspectionFix(String ID, LocalizeValue text, Class<? extends PyElement> containerClass) {
        super(ID, false);
        setText(text);
        myContainerClass = containerClass;
    }

    @Override
    public PsiElement getContainer(PsiElement context) {
        return PsiTreeUtil.getParentOfType(context, myContainerClass);
    }
}
