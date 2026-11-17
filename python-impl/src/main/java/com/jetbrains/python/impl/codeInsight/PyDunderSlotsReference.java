// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;


public class PyDunderSlotsReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
    public PyDunderSlotsReference(PyStringLiteralExpression element) {
        super(element, element.getStringValueTextRanges().get(0));
    }

    @Override
    public @Nullable PsiElement resolve() {
        PyClass referenceClass = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
        return referenceClass == null ? null : referenceClass.findInstanceAttribute(myElement.getStringValue(), true);
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        if (element instanceof PyTargetExpression targetExpression && PyUtil.isInstanceAttribute(targetExpression)) {
            PyClass elementClass = PsiTreeUtil.getParentOfType(targetExpression, PyClass.class);
            PyClass referenceClass = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
            if (referenceClass != null && referenceClass.isSubclass(elementClass, null)) {
                String elementName = targetExpression.getReferencedName();
                String referenceName = myElement.getStringValue();
                if (Objects.equals(elementName, referenceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable String getUnresolvedDescription() {
        return null;
    }
}
