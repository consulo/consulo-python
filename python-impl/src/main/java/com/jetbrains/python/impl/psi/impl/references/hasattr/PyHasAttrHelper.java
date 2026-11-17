// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references.hasattr;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PyHasAttrHelper {
    public static final PyHasAttrHelper INSTANCE = new PyHasAttrHelper();

    private PyHasAttrHelper() {
    }

    public Set<String> getNamesFromHasAttrs(PsiElement psiElement, PyExpression qualifier) {
        return getHasAttrVariantsFromContext(psiElement, qualifier);
    }

    private Set<String> getHasAttrVariantsFromContext(PsiElement psiElement, PyExpression qualifier) {
        Set<String> result = new HashSet<>();
        result.addAll(getHasAttrVariantsFromAnd(psiElement, qualifier));
        result.addAll(getHasAttrVariantsFromConditions(psiElement, qualifier));
        return result;
    }

    private Set<String> getHasAttrVariantsFromAnd(PsiElement psiElement, PyExpression qualifier) {
        Set<String> result = new HashSet<>();
        PyBinaryExpression binaryExpr = PsiTreeUtil.getParentOfType(psiElement, PyBinaryExpression.class);
        if (binaryExpr == null) {
            return result;
        }
        if (!binaryExpr.isOperator(PyNames.AND)) {
            return result;
        }
        if (!PsiTreeUtil.isAncestor(binaryExpr.getRightExpression(), psiElement, false)) {
            return result;
        }
        result.addAll(getHasAttrVisitorResultOn(binaryExpr.getLeftExpression(), qualifier));
        return result;
    }

    private Set<String> getHasAttrVariantsFromConditions(PsiElement psiElement, PyExpression qualifier) {
        Set<String> result = new HashSet<>();

        PsiElement curParent = PsiTreeUtil.getParentOfType(psiElement, PyIfPart.class, PyConditionalExpression.class);
        while (curParent != null) {
            PyExpression condition;
            if (curParent instanceof PyIfPart ifPart && PsiTreeUtil.isAncestor(ifPart.getStatementList(), psiElement, true)) {
                condition = ifPart.getCondition();
            }
            else if (curParent instanceof PyConditionalExpression conditionalExpression &&
                     PsiTreeUtil.isAncestor(conditionalExpression.getTruePart(), psiElement, false)) {
                condition = conditionalExpression.getCondition();
            }
            else {
                condition = null;
            }
            if (condition != null) {
                result.addAll(getHasAttrVisitorResultOn(condition, qualifier));
            }
            curParent = PsiTreeUtil.getParentOfType(curParent, PyIfPart.class, PyConditionalExpression.class);
        }

        return result;
    }

    private Set<String> getHasAttrVisitorResultOn(PsiElement psiElement, PyExpression qualifier) {
        if (!(qualifier instanceof PyReferenceExpression referenceExpression)) {
            return Collections.emptySet();
        }
        PsiElement resolvedQualifier = referenceExpression.getReference().resolve();
        if (resolvedQualifier == null) {
            return Collections.emptySet();
        }
        PyHasAttrVisitor pyHasAttrVisitor = new PyHasAttrVisitor(resolvedQualifier);
        psiElement.accept(pyHasAttrVisitor);
        return pyHasAttrVisitor.getResult();
    }
}
