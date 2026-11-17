// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

public class PyCaseClauseImpl extends PyElementImpl implements PyCaseClause, PyCaptureContext {
    public PyCaseClauseImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyCaseClause(this);
    }

    @Override
    public @Nullable PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context) {
        return getSubjectTypeBefore(context);
    }

    private @Nullable PyType getSubjectTypeBefore(TypeEvalContext context) {
        PyCaseClause prevClause = PsiTreeUtil.getPrevSiblingOfType(this, PyCaseClause.class);
        if (prevClause != null) {
            return prevClause.getSubjectTypeAfter(context);
        }
        else {
            if (!(getParent() instanceof PyMatchStatement matchStatement)) {
                return null;
            }
            PyExpression subject = matchStatement.getSubject();
            if (subject == null) {
                return null;
            }
            return context.getType(subject);
        }
    }

    @Override
    public @Nullable PyType getSubjectTypeAfter(TypeEvalContext context) {
        return PyUtil.getNullableParameterizedCachedValue(this, context, ctx -> getSubjectTypeAfterNoCache(context));
    }

    private @Nullable PyType getSubjectTypeAfterNoCache(TypeEvalContext context) {
        PyType beforeType = getSubjectTypeBefore(context);
        PyPattern pattern = getPattern();
        if (pattern == null) {
            return beforeType;
        }
        PyExpression guardCondition = getGuardCondition();
        if (guardCondition != null && !PyEvaluator.evaluateAsBoolean(guardCondition, false)) {
            return beforeType;
        }
        // because subject can be 'Any', and then negative narrowing won't help
        if (pattern.isIrrefutable()) {
            return PyNeverType.NEVER;
        }

        if (pattern.canExcludePatternType(context)) {
            PyType patternType = context.getType(pattern);
            Ref<PyType> narrowing = PyTypeAssertionEvaluator.createAssertionType(beforeType, patternType, false, true, context);
            if (narrowing != null) {
                return narrowing.get();
            }
        }
        return beforeType;
    }
}
