// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Top-level declarations of {@code StringLiteralExpressionExt.kt}.
 */
public final class StringLiteralExpressionExtKt {
    private StringLiteralExpressionExtKt() {
    }

    /**
     * If literal is argument of some function call -- return callee
     */
    public static @Nullable PyExpression getCalleeIfArgument(StringLiteralExpression literal, int argumentPosition) {
        return getCalleeIfArgument(literal, argumentPosition, null);
    }

    /**
     * If literal is argument of some function call -- return callee
     */
    public static @Nullable PyExpression getCalleeIfArgument(StringLiteralExpression literal,
                                                             int argumentPosition,
                                                             @Nullable String argumentName) {
        PsiElement parent = literal.getParent();
        if (parent == null) {
            return null;
        }

        PyArgumentList argumentList;
        if (parent instanceof PyArgumentList list) {
            argumentList = list;
        }
        else if (parent instanceof PyKeywordArgument) {
            argumentList = parent.getParent() instanceof PyArgumentList list ? list : null;
        }
        else {
            argumentList = null;
        }
        if (argumentList == null) {
            return null;
        }

        PyExpression[] arguments = argumentList.getArguments();
        PyExpression positional = argumentPosition >= 0 && argumentPosition < arguments.length ? arguments[argumentPosition] : null;
        PyExpression keywordValue = null;
        if (argumentName != null) {
            PyKeywordArgument keywordArgument = argumentList.getKeywordArgument(argumentName);
            keywordValue = keywordArgument != null ? keywordArgument.getValueExpression() : null;
        }
        if (literal == positional || literal == keywordValue) {
            PyCallExpression callExpression = argumentList.getCallExpression();
            return callExpression != null ? callExpression.getCallee() : null;
        }
        return null;
    }

    /**
     * @see #getCalleeIfArgument(StringLiteralExpression, int, String)
     */
    public static @Nullable PyExpression getCalleeIfArgument(StringLiteralExpression literal, FunctionParameter argument) {
        return getCalleeIfArgument(literal, argument.getPosition(), argument.getName());
    }
}
