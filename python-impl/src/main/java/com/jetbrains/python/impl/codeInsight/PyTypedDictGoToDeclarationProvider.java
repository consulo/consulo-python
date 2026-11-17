// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.navigation.GotoDeclarationHandlerBase;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PyTypedDictGoToDeclarationProvider extends GotoDeclarationHandlerBase {
    @Override
    @RequiredReadAction
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, @Nullable Editor editor) {
        PsiElement pyReferenceElement = sourceElement != null ? sourceElement.getParent() : null;
        if (!(pyReferenceElement instanceof PyReferenceExpression referenceExpression) || editor == null) {
            return null;
        }
        if (!(referenceExpression.getReference().resolve() instanceof PyClass resolvedClass)) {
            return null;
        }
        if (PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor(
            resolvedClass,
            TypeEvalContext.userInitiated(sourceElement.getProject(), sourceElement.getContainingFile())
        )) {
            return resolvedClass;
        }

        return null;
    }
}
