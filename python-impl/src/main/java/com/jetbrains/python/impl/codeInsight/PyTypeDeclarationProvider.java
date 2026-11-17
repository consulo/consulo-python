// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.action.TypeDeclarationProvider;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@ExtensionImpl
public class PyTypeDeclarationProvider extends TypeDeclarationProvider {

    @Override
    @RequiredReadAction
    public PsiElement @Nullable [] getSymbolTypeDeclarations(PsiElement symbol, @Nullable Editor editor, int offset) {
        if (symbol instanceof PyTypedElement typedElement) {
            TypeEvalContext context = TypeEvalContext.userInitiated(symbol.getProject(), symbol.getContainingFile());

            // When the symbol is a function,
            // navigate to the return type declaration instead of the function type itself.
            PyType symbolType = context.getType(typedElement);
            PyType type = symbolType instanceof PyCallableType callableType && !(symbolType instanceof PyClassType)
                ? callableType.getReturnType(context)
                : symbolType;

            PsiElement[] declarations = PyTypeUtil.getCompositeComponentSequence(type).stream()
                .filter(Objects::nonNull)
                .map(PyType::getDeclarationElement)
                .filter(Objects::nonNull)
                .distinct()
                .toArray(PsiElement[]::new);
            return declarations.length > 0 ? declarations : null;
        }

        return null;
    }
}
