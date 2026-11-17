// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.RemoveDecoratorQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * Inspection to detect occurrences of @classmethod and @staticmethod
 * on methods outside of a class
 */
@ExtensionImpl
public final class PyDecoratorInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDecoratorOutsideClass();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            PyClass containingClass = node.getContainingClass();
            if (containingClass != null) {
                return;
            }

            PyDecoratorList decorators = node.getDecoratorList();
            if (decorators == null) {
                return;
            }
            for (PyDecorator decorator : decorators.getDecorators()) {
                String name = decorator.getText();
                if (name.equals("@classmethod") || name.equals("@staticmethod")) {
                    registerProblem(decorator, PyLocalize.inspDecoratorsMethodOnlyDecoratorOnMethodOutsideClass(name).get(),
                                    new RemoveDecoratorQuickFix());
                }
            }
        }
    }
}
