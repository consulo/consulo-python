/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Detect and report incompatibilities between __new__ and __init__ signatures.
 *
 * @author dcheryasov
 */
@ExtensionImpl
public class PyInitNewSignatureInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNewInitSignature();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            String functionName = node.getName();
            if (!PyNames.NEW.equals(functionName) && !PyNames.INIT.equals(functionName)) {
                return;
            }
            PyClass cls = node.getContainingClass();
            if (cls == null) {
                return;
            }
            if (!cls.isNewStyleClass(null)) {
                return;
            }
            String complementaryName = PyNames.NEW.equals(functionName) ? PyNames.INIT : PyNames.NEW;
            PyFunction complementaryMethod = cls.findMethodByName(complementaryName, true, null);
            if (complementaryMethod == null || PyUtil.isObjectClass(ObjectUtil.assertNotNull(complementaryMethod.getContainingClass()))) {
                return;
            }
            if (!PyUtil.isSignatureCompatibleTo(complementaryMethod, node, myTypeEvalContext) &&
                !PyUtil.isSignatureCompatibleTo(node, complementaryMethod, myTypeEvalContext) &&
                node.getContainingFile() == cls.getContainingFile()) {
                registerProblem(
                    node.getParameterList(),
                    PyNames.NEW.equals(node.getName())
                        ? PyLocalize.inspNewIncompatibleToInit().get()
                        : PyLocalize.inspInitIncompatibleToNew().get(),
                    new PyChangeSignatureQuickFix(false)
                );
            }
        }
    }
}