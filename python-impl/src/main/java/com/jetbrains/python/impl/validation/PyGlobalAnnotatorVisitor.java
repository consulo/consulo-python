// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotates errors in 'global' statements.
 */
public class PyGlobalAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyGlobalAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyGlobalStatement(PyGlobalStatement node) {
        PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
        if (function != null) {
            PyParameterList paramList = function.getParameterList();
            // collect param names
            Set<String> paramNames = new HashSet<>();

            ParamHelper.walkDownParamArray(
                paramList.getParameters(),
                new ParamHelper.ParamVisitor() {
                    @Override
                    public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                        paramNames.add(param.getName());
                    }
                }
            );

            // check globals
            for (PyTargetExpression expr : node.getGlobals()) {
                String expr_name = expr.getReferencedName();
                if (paramNames.contains(expr_name)) {
                    myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annNameUsedBothAsGlobalAndParam(expr_name).get())
                        .range(expr).create();
                }
            }
        }
    }
}
