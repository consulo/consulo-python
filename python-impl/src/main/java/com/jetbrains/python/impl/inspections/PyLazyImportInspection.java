// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
  * Reports invalid placement of {@code lazy} imports (PEP 810):
  * <ul>
  *   <li>inside a function, class, {@code try}/{@code except}/{@code finally} body, or {@code match}/{@code case} body</li>
  *   <li>{@code lazy from ... import *}</li>
  *   <li>{@code lazy from __future__ import ...}</li>
  * </ul>
  */
@ExtensionImpl
public final class PyLazyImportInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameLazyImport();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    /**
      * PSI containers in which a {@code lazy} import (PEP 810) is forbidden: anything but the module level.
      */
    @SuppressWarnings("unchecked")
    public static final Class<? extends PsiElement>[] FORBIDDEN_LAZY_IMPORT_CONTAINERS = new Class[] {
        PyFunction.class, PyClass.class, PyTryPart.class, PyExceptPart.class, PyFinallyPart.class, PyMatchStatement.class
      };

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static final class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyImportStatement(PyImportStatement node) {
            if (!node.isLazy()) {
                return;
            }
            checkPlacement(node);
        }

        @Override
        public void visitPyFromImportStatement(PyFromImportStatement node) {
            if (!node.isLazy()) {
                return;
            }
            if (node.isStarImport()) {
                registerProblem(anchor(node), PyLocalize.inspLazyImportCannotBeStarImport().get(),
                                ProblemHighlightType.GENERIC_ERROR);
                return;
            }
            if (node.isFromFuture()) {
                registerProblem(anchor(node), PyLocalize.inspLazyImportCannotImportFromFuture().get(),
                                ProblemHighlightType.GENERIC_ERROR);
                return;
            }
            checkPlacement(node);
        }

        private void checkPlacement(PyStatement node) {
            if (PsiTreeUtil.getParentOfType(node, FORBIDDEN_LAZY_IMPORT_CONTAINERS) != null) {
                registerProblem(anchor(node), PyLocalize.inspLazyImportMustAppearAtModuleLevel().get(),
                                ProblemHighlightType.GENERIC_ERROR);
            }
        }

        private static PsiElement anchor(PyStatement node) {
            PsiElement first = node.getFirstChild();
            return first != null ? first : node;
        }
    }
}
