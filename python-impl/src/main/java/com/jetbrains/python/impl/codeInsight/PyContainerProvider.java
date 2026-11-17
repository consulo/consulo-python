// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.search.ContainerProvider;
import consulo.language.psi.stub.StubElement;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyContainerProvider implements ContainerProvider {
    @Override
    public @Nullable PsiElement getContainer(PsiElement item) {
        if (item instanceof PyElement && PyPsiUtils.isStubBasedElement(item) /* Consulo: see PyPsiUtils.isStubBasedElement */) {
            return getContainerByStub((StubBasedPsiElement) item);
        }
        return null;
    }

    private static @Nullable PsiElement getContainerByStub(StubBasedPsiElement element) {
        StubElement stub = element.getStub();
        if (stub != null) {
            StubElement parentStub = stub.getParentStub();
            if (parentStub != null) {
                return parentStub.getPsi();
            }
        }
        return null;
    }
}
