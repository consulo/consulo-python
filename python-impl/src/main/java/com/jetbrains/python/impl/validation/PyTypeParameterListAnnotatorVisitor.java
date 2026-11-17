// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;

import java.util.HashSet;
import java.util.Set;

public class PyTypeParameterListAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyTypeParameterListAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyTypeParameterList(PyTypeParameterList node) {
        if (!node.getTypeParameters().isEmpty()) {
            Set<String> namesSet = new HashSet<>();

            for (PyTypeParameter typeParameter : node.getTypeParameters()) {
                String name = typeParameter.getName();
                PsiElement identifier = typeParameter.getNameIdentifier();
                if (name != null && identifier != null) {
                    if (!namesSet.add(name)) {
                        myHolder.newAnnotation(HighlightSeverity.ERROR,
                                PyLocalize.typeParamListAnnotatorTypeParameterAlreadyDefined(name).get())
                            .range(typeParameter.getNameIdentifier().getTextRange()).create();
                    }
                }
            }
        }
    }

    @Override
    public void visitPyTypeParameter(PyTypeParameter node) {
        PyExpression boundExpression = PyPsiUtils.flattenParens(node.getBoundExpression());
        if (node.getKind() == PyAstTypeParameter.Kind.TypeVar) {

            if (boundExpression instanceof PyTupleExpression tupleExpression) {
                if (tupleExpression.getElements().length < 2) {
                    myHolder.newAnnotation(HighlightSeverity.ERROR,
                            PyLocalize.typeParamListAnnotatorTwoOrMoreTypesRequired().get())
                        .range(boundExpression.getTextRange()).create();
                }
            }
        }
        else if (boundExpression != null) {
            String name = node.getKind() == PyAstTypeParameter.Kind.ParamSpec ? "Parameter specifications" : "Type variable tuples";
            myHolder.newAnnotation(HighlightSeverity.ERROR,
                    PyLocalize.typeParamListAnnotatorTypeVarTupleAndParamSpecCanNotHaveBounds(name).get())
                .range(boundExpression.getTextRange()).create();
        }
    }
}
