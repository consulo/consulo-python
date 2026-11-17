// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;


public class PyFunctionInsertHandler extends ParenthesesInsertHandler<LookupElement> {
    public static PyFunctionInsertHandler INSTANCE = new PyFunctionInsertHandler();

    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
        super.handleInsert(context, item);
        if (hasParams(context, item)) {
            AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), getFunction(item));
        }
    }

    @Override
    protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
        return hasParams(context, item);
    }

    private static boolean hasParams(InsertionContext context, LookupElement item) {
        PyFunction function = getFunction(item);
        return function != null && hasParams(context, function);
    }

    public static boolean hasParams(InsertionContext context, PyFunction function) {
        PsiElement element = context.getFile().findElementAt(context.getStartOffset());
        PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
        int implicitArgsCount;
        if (refExpr != null) {
            TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(refExpr.getProject());
            PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);
            implicitArgsCount = PyCallExpressionHelper.getImplicitArgumentCount(refExpr, function, resolveContext);
        }
        else {
            implicitArgsCount = 0;
        }
        return function.getParameterList().getParameters().length > implicitArgsCount;
    }

    private static @Nullable PyFunction getFunction(LookupElement item) {
        return PyUtil.as(item.getPsiElement(), PyFunction.class);
    }
}
