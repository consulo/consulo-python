// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageViewLongNameLocation;
import consulo.usage.UsageViewShortNameLocation;
import consulo.usage.UsageViewTypeLocation;

/**
 * Provides description for magic literals.
 * <strong>Install it</strong> as {@link ElementDescriptionProvider} extension
 *
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public final class PyMagicLiteralElementDescriptionProvider implements ElementDescriptionProvider {
    @Override
    @RequiredReadAction
    public LocalizeValue getElementDescription(PsiElement element, ElementDescriptionLocation location) {
        if (element instanceof PyStringLiteralExpression) {
            PyMagicLiteralExtensionPoint point = PyMagicLiteralTools.getPoint((PyStringLiteralExpression) element);
            if (point != null) {
                if (location instanceof UsageViewTypeLocation) {
                    return LocalizeValue.of(point.getLiteralType());
                }
                if ((location instanceof UsageViewShortNameLocation) || (location instanceof UsageViewLongNameLocation)) {
                    return LocalizeValue.of(((StringLiteralExpression) element).getStringValue());
                }
            }
        }
        return LocalizeValue.empty();
    }
}
