// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyTypingNewTypeFactoryType;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.Objects;

@ExtensionImpl
public class PyNewTypeInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNewType();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new PyInspectionVisitor(holder, context) {
            @Override
            @RequiredReadAction
            public void visitPyTargetExpression(PyTargetExpression node) {
                if (!(node.findAssignedValue() instanceof PyCallExpression assignedValue)) {
                    return;
                }

                if (!(assignedValue.getCallee() instanceof PyReferenceExpression callee)) {
                    return;
                }
                PsiElement resolved = callee.followAssignmentsChain(getResolveContext()).getElement();
                if (resolved == null) {
                    return;
                }
                boolean isNewTypeCall = resolved instanceof PyQualifiedNameOwner qualifiedNameOwner &&
                    PyTypingTypeProvider.NEW_TYPE.equals(qualifiedNameOwner.getQualifiedName());

                if (isNewTypeCall) {
                    String newTypeName = PyResolveUtil.resolveStrArgument(assignedValue, 0, "name");
                    String targetName = node.getName();
                    if (!Objects.equals(targetName, newTypeName)) {
                        registerProblem(node.getNameIdentifier(),
                                        PyInspectionMessages.problemMessage(
                                            p -> PyLocalize.inspNameNewTypeVariableNameDoesNotMatchNewTypeName(p[0], p[1]),
                                            String.valueOf(targetName), String.valueOf(newTypeName)));
                    }

                    PyExpression typeExpr = PyPsiUtils.flattenParens(assignedValue.getArgument(1, "tp", PyExpression.class));
                    if (typeExpr != null) {
                        PyType type = myTypeEvalContext.getType(typeExpr);
                        if (type instanceof PyTypingNewTypeFactoryType) {
                            return;
                        }
                        if (!(type instanceof PyClassType classType) || !classType.isDefinition()) {
                            registerProblem(typeExpr,
                                            PyInspectionMessages.problemMessage(p -> PyLocalize.inspNameNewTypeExpectedClass()));
                        }
                        else if (classType.getTypeArguments().stream()
                            .anyMatch(it -> it instanceof PyTypeVarType typeVarType && typeVarType.getScopeOwner() == null)) {
                            registerProblem(typeExpr,
                                            PyInspectionMessages.problemMessage(p -> PyLocalize.inspNameNewTypeNewTypeCannotBeGeneric()));
                        }
                        else if (type instanceof PyLiteralType literalType) {
                            registerProblem(typeExpr,
                                            PyInspectionMessages.problemMessage(
                                                p -> PyLocalize.inspNameNewTypeNewTypeCannotBeUsedWith(p[0]),
                                                String.valueOf(literalType.getName())));
                        }
                        else if (type instanceof PyTypedDictType) {
                            registerProblem(typeExpr,
                                            PyInspectionMessages.problemMessage(
                                                p -> PyLocalize.inspNameNewTypeNewTypeCannotBeUsedWith(p[0]), "TypedDict"));
                        }
                    }
                }
            }

            @Override
            @RequiredReadAction
            public void visitPyClass(PyClass node) {
                for (PyExpression superClassExpression : PyClassImpl.getUnfoldedSuperClassExpressions(node)) {
                    PyType superClassType = myTypeEvalContext.getType(superClassExpression);
                    if (superClassType instanceof PyTypingNewTypeFactoryType newTypeFactoryType) {
                        registerProblem(superClassExpression,
                                        PyInspectionMessages.problemMessage(
                                            p -> PyLocalize.inspNameNewTypeCannotBeSubclassed(p[0]),
                                            String.valueOf(newTypeFactoryType.getName())),
                                        ProblemHighlightType.GENERIC_ERROR);
                    }
                }
            }
        };
    }
}
