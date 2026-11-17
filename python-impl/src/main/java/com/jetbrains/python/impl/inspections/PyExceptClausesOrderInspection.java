// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.quickfix.PyMoveExceptQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public final class PyExceptClausesOrderInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameBadExceptClausesOrder();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonExceptions();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyTryExceptStatement(PyTryExceptStatement node) {
            PyExceptPart[] exceptParts = node.getExceptParts();
            if (exceptParts.length > 1) {
                Set<PyClass> exceptClasses = new HashSet<>();
                for (PyExceptPart exceptPart : exceptParts) {
                    PyExpression exceptClass = exceptPart.getExceptClass();
                    if (exceptClass instanceof PyReferenceExpression) {
                        PsiElement element = ((PyReferenceExpression)exceptClass).followAssignmentsChain(getResolveContext()).getElement();
                        if (element instanceof PyClass pyClass) {
                            if (exceptClasses.contains(pyClass)) {
                                registerProblem(exceptClass, PyInspectionMessages.problemMessage(a -> PyLocalize.inspBadExceptExceptionClassAlreadyCaught(a[0]), CodifiedParam.ofReference(pyClass)));
                            }
                            else {
                                for (PyClass superClass : pyClass.getSuperClasses(null)) {
                                    if (exceptClasses.contains(superClass)) {
                                        registerProblem(exceptClass,
                                                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspBadExceptSuperclassOfExceptionClassAlreadyCaught(a[0], a[1]), CodifiedParam.ofReference(superClass), CodifiedParam.ofReference(pyClass)),
                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                        new PyMoveExceptQuickFix());
                                    }
                                }
                            }
                            exceptClasses.add(pyClass);
                        }
                    }
                }
            }
        }
    }
}
