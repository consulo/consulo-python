// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class PyCustomMemberProvider {
    public static PyCustomMemberProvider getInstance() {
        return Application.get().getInstance(PyCustomMemberProvider.class);
    }

    public abstract boolean isReferenceToMe(PsiReference reference, PyCustomMember member);

    public abstract PyTypedElement createPyCustomMemberTarget(
        PyCustomMember member,
        @Nullable PyClass clazz,
        PsiElement context,
        @Nullable PsiElement resolveTarget,
        @Nullable Function<? super PsiElement, ? extends PyType> typeCallback,
        @Nullable PyCustomMemberTypeInfo<?> customTypeInfo,
        boolean resolveToInstance
    );
}
