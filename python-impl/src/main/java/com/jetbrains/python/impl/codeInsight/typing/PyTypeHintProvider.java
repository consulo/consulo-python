// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyTypeHintProvider {
    ExtensionPointName<PyTypeHintProvider> EP_NAME = ExtensionPointName.create(PyTypeHintProvider.class);

    @Nullable
    Ref<@Nullable PyType> parseTypeHint(PyExpression typeHint,
                                        @Nullable PyQualifiedNameOwner alias,
                                        PsiElement resolved,
                                        TypeEvalContext context);

    static @Nullable Ref<@Nullable PyType> parseTypeHintFromProviders(PyExpression typeHint,
                                                                      @Nullable PyQualifiedNameOwner alias,
                                                                      PsiElement resolved,
                                                                      TypeEvalContext context) {
        return EP_NAME.computeSafeIfAny(it -> it.parseTypeHint(typeHint, alias, resolved, context));
    }
}
