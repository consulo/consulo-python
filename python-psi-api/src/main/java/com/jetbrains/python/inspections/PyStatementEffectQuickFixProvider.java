// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.inspection.LocalQuickFix;
import org.jspecify.annotations.Nullable;


@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyStatementEffectQuickFixProvider {
    ExtensionPointName<PyStatementEffectQuickFixProvider> EP_NAME = ExtensionPointName.create(PyStatementEffectQuickFixProvider.class);


    @Nullable
    LocalQuickFix getQuickFix(PyExpressionStatement expressionStatement);
}
