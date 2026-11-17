// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.Stack;
import org.jspecify.annotations.Nullable;

/**
 * Inspection to detect too broad except clause
 * such as no exception class specified, or specified as 'Exception'
 */
@ExtensionImpl
public final class PyBroadExceptionInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTooBroadExceptionClauses();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonExceptions();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    public static boolean equalsException(PyClass cls, TypeEvalContext context) {
        PyType type = context.getType(cls);
        return ("Exception".equals(cls.getName()) || "BaseException".equals(cls.getName())) && type != null && type.isBuiltin();
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyExceptBlock(PyExceptPart node) {
            PyExpression exceptClass = node.getExceptClass();
            if (reRaised(node)) {
                return;
            }
            if (exceptClass == null) {
                registerProblem(node.getFirstChild(), PyLocalize.inspTooBroadExceptionClause().get());
            }
            if (exceptClass instanceof PyReferenceExpression) {
                PyType type = myTypeEvalContext.getType(exceptClass);
                if (type instanceof PyClassType) {
                    PyClass cls = ((PyClassType) type).getPyClass();
                    PyExpression target = node.getTarget();
                    if (equalsException(cls, myTypeEvalContext) && (target == null || !isExceptionUsed(node, target.getText()))) {
                        registerProblem(exceptClass, PyLocalize.inspTooBroadExceptionClause().get());
                    }
                }
            }
        }

        private static boolean reRaised(PyExceptPart node) {
            PyStatementList statementList = node.getStatementList();
            for (PyStatement st : statementList.getStatements()) {
                if (st instanceof PyRaiseStatement) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isExceptionUsed(PyExceptPart node, String text) {
            Stack<PsiElement> stack = new Stack<>();
            PyStatementList statementList = node.getStatementList();
            for (PyStatement st : statementList.getStatements()) {
                stack.push(st);
                while (!stack.isEmpty()) {
                    PsiElement e = stack.pop();
                    if (e instanceof PyReferenceExpression ref) {
                        PsiElement resolved = ref.getReference().resolve();
                        if (resolved != null) {
                            if (text.equals(resolved.getText())) {
                                return true;
                            }
                        }
                    }
                    for (PsiElement psiElement : e.getChildren()) {
                        stack.push(psiElement);
                    }
                }
            }
            return false;
        }
    }
}
