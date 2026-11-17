// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

/**
 * Any magic literal extension point should imlement this interface
 *
 * @author Ilya.Kazakevich
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyMagicLiteralExtensionPoint {
    ExtensionPointName<PyMagicLiteralExtensionPoint> EP_NAME = ExtensionPointName.create(PyMagicLiteralExtensionPoint.class);

    /**
     * Checks if literal is magic and supported by this extension point.
     *
     * @param element element to check
     * @return true if magic.
     */
    boolean isMagicLiteral(StringLiteralExpression element);

    /**
     * @return human-readable type of this literal. Actually, that is extension point name
     */
    String getLiteralType();

    boolean isEnabled(PsiElement anchor);
}
