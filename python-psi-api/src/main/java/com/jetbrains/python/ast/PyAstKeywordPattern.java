// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

public interface PyAstKeywordPattern extends PyAstPattern {
    @RequiredReadAction
    default String getKeyword() {
        return getKeywordElement().getText();
    }

    @RequiredReadAction
    default PsiElement getKeywordElement() {
        return getFirstChild();
    }

    @RequiredReadAction
    default @Nullable PyAstPattern getValuePattern() {
        return findChildByClass(this, PyAstPattern.class);
    }

    @Override
    default boolean isIrrefutable() {
        return false;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyKeywordPattern(this);
    }
}
