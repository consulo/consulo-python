// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyDefaultArgumentQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyDefaultArgumentInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDefaultArgument();
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
        public void visitPyNamedParameter(PyNamedParameter node) {
            PyExpression defaultValue = node.getDefaultValue();
            if (PyUtil.isForbiddenMutableDefault(defaultValue, myTypeEvalContext)) {
                registerProblem(defaultValue, PyLocalize.inspDefaultArgumentsDefaultArgumentValueMutable().get(),
                                new PyDefaultArgumentQuickFix());
            }
        }
    }
}
