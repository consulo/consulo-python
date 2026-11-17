// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.Collection;

@ExtensionImpl
public class PyOverridesInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInvalidUsagesOfOverrideDecorator();
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
        return new Visitor(holder, context);
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);

            PyClass containingClass = node.getContainingClass();
            if (containingClass != null && containingClass.getAncestorTypes(myTypeEvalContext).contains(null)) {
                return;
            }

            PyDecorator overrideDecorator = PyKnownDecoratorUtil.findOverrideDecorator(node, myTypeEvalContext);
            if (overrideDecorator == null) {
                return;
            }

            Collection<PsiElement> superMethods = PySuperMethodsSearch.search(node, myTypeEvalContext).findAll();
            if (superMethods.isEmpty()) {
                registerProblem(overrideDecorator, PyLocalize.inspOverrideMissingSuperMethod().get());
            }
        }
    }
}
