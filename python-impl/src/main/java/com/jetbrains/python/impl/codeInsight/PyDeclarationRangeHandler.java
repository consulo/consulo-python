// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.hint.DeclarationRangeHandler;
import consulo.language.psi.PsiElement;

/**
 * Consulo: {@link DeclarationRangeHandler} is registered per element class, so JB's single handler registered for
 * keys {@code PyClass} and {@code PyFunction} is expressed by the {@link ForClass} and {@link ForFunction} subclasses.
 */
public abstract class PyDeclarationRangeHandler<T extends PsiElement> implements DeclarationRangeHandler<T> {
    @Override
    public TextRange getDeclarationRange(T container) {
        int start = container.getTextRange().getStartOffset();
        if (container instanceof PyFunction) {
            PyParameterList parameterList = ((PyFunction) container).getParameterList();
            return new TextRange(start, parameterList.getTextRange().getEndOffset());
        }
        if (container instanceof PyClass) {
            PyArgumentList argumentList = ((PyClass) container).getSuperClassExpressionList();
            if (argumentList != null) {
                return new TextRange(start, argumentList.getTextRange().getEndOffset());
            }
            ASTNode nameNode = ((PyClass) container).getNameNode();
            if (nameNode != null) {
                return new TextRange(start, nameNode.getStartOffset() + nameNode.getTextLength());
            }
        }
        return container.getTextRange();
    }

    @ExtensionImpl
    public static final class ForClass extends PyDeclarationRangeHandler<PyClass> {
        @Override
        public Class<PyClass> getElementClass() {
            return PyClass.class;
        }
    }

    @ExtensionImpl
    public static final class ForFunction extends PyDeclarationRangeHandler<PyFunction> {
        @Override
        public Class<PyFunction> getElementClass() {
            return PyFunction.class;
        }
    }
}
