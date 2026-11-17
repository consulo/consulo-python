// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.smartstepinto;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.execution.debug.step.XSmartStepIntoVariant;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An abstract class for a thing we can smart step into.
 * <p>
 * Should you need to support another Python expression as a smart step into target,
 * subclass this class and add the respective method override to {@code PySmartStepIntoVariantVisitor}.
 *
 */
public abstract class PySmartStepIntoVariant extends XSmartStepIntoVariant {
    protected final PsiElement myElement;
    protected final int myCallOrder;
    protected final PySmartStepIntoContext myContext;

    protected PySmartStepIntoVariant(PsiElement element, int callOrder, PySmartStepIntoContext context) {
        myElement = element;
        myCallOrder = callOrder;
        myContext = context;
    }

    public abstract @Nullable String getFunctionName();

    public int getCallOrder() {
        return myCallOrder;
    }

    public PySmartStepIntoContext getContext() {
        return myContext;
    }

    @Override
    public @Nullable TextRange getHighlightRange() {
        return myElement.getTextRange();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PySmartStepIntoVariant variant)) {
            return false;
        }
        return myElement.equals(variant.myElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myElement);
    }
}
