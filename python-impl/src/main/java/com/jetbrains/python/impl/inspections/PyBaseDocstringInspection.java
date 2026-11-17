// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.testing.PythonUnitTestDetectorsKt;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseDocstringInspection extends PyInspection {
    @Override
    public abstract Visitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state);

    protected abstract static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder,
                       TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public final void visitPyFile(PyFile node) {
            checkDocString(node);
        }

        @Override
        public final void visitPyFunction(PyFunction node) {
            if (PythonUnitTestDetectorsKt.isTestFunction(node)) {
                return;
            }
            Property property = node.getProperty();
            if (property != null && (node == property.getSetter().valueOrNull() || node == property.getDeleter().valueOrNull())) {
                return;
            }
            String name = node.getName();
            if (name != null && !name.startsWith("_")) {
                checkDocString(node);
            }
        }

        @Override
        public final void visitPyClass(PyClass node) {
            if (PythonUnitTestDetectorsKt.isTestClass(node, myTypeEvalContext)) {
                return;
            }
            String name = node.getName();
            if (name == null || name.startsWith("_")) {
                return;
            }
            checkDocString(node);
        }

        protected abstract void checkDocString(PyDocStringOwner node);
    }
}
