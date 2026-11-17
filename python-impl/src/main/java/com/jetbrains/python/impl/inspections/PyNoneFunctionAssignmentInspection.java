// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyRemoveAssignmentQuickFix;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt.isElementInSkeletons;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

/**
 * User: ktisha
 * <p>
 * pylint E1111
 * <p>
 * Used when an assignment is done on a function call but the inferred function doesn't return anything.
 * Also reports when the result of such a function is used in other contexts (e.g., as an argument, in f-strings).
 */
@ExtensionImpl
public class PyNoneFunctionAssignmentInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNoneFunctionAssignment();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        private final Map<PyFunction, Boolean> myHasInheritors = new HashMap<>();

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyCallExpression(PyCallExpression call) {
            if (!isReturnValueUsed(call)) {
                return;
            }

            PyType type = myTypeEvalContext.getType(call);
            PyExpression callee = call.getCallee();

            if (!isNoneType(type) || callee == null) {
                return;
            }
            List<PyCallable> callables = call.multiResolveCalleeFunction(getResolveContext());
            if (callables.isEmpty()) {
                return;
            }
            for (PyCallable callable : callables) {
                if (!isNoneType(myTypeEvalContext.getReturnType(callable)) ||
                    isElementInSkeletons(callable) ||
                    callable instanceof PyFunction function && hasInheritors(function)) {
                    return;
                }
            }
            PsiElement parent = call.getParent();
            if (parent instanceof PyLambdaExpression) {
                return;
            }
            else if (parent instanceof PyAssignmentStatement) {
                registerProblem(
                    parent, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspNoneFunctionAssignment(msgArgs[0]), callee.getName()),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new PyRemoveAssignmentQuickFix()
                );
            }
            else {
                registerProblem(call, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspNoneFunctionAssignment(msgArgs[0]), callee.getName()));
            }
        }

        private boolean hasInheritors(PyFunction function) {
            return myHasInheritors.computeIfAbsent(function, f -> PyOverridingMethodsSearch.search(f, true).findFirst() != null);
        }

        /**
         * Checks if the return value of the call expression is used.
         * Returns false if the call is a standalone expression statement.
         */
        private static boolean isReturnValueUsed(PyCallExpression call) {
            // Standalone expression statement - value not used
            return !(getParentSkippingParentheses(call) instanceof PyExpressionStatement);
        }

        private static PsiElement getParentSkippingParentheses(PsiElement element) {
            PsiElement parent = element.getParent();
            while (parent instanceof PyParenthesizedExpression) {
                parent = parent.getParent();
            }
            return parent;
        }
    }
}
