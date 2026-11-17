// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
  * User: ktisha
  * <p>
  * pylint W0601
  */
@ExtensionImpl
public final class PyGlobalUndefinedInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameGlobalUndefined();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
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


    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyGlobalStatement(PyGlobalStatement node) {
            PyTargetExpression[] globals = node.getGlobals();

            for (PyTargetExpression global : globals) {
                PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

                List<PsiElement> elements = PyUtil.multiResolveTopPriority(global.getReference(resolveContext));
                boolean noTopLevelDeclaration = elements.stream().noneMatch(PyUtil::isTopLevel);

                if (noTopLevelDeclaration) {
                    registerProblem(global, PyInspectionMessages.problemMessage(a -> PyLocalize.inspGlobalVariableUndefined(a[0]), global.getName()));
                }
            }
        }
    }
}
