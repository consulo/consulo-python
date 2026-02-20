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
import com.jetbrains.python.impl.inspections.quickfix.AddMethodQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * See pylint W0232
 *
 * @author ktisha
 */
@ExtensionImpl
public class PyClassHasNoInitInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameClassHasNoInit();
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

    private static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyClass(PyClass node) {
            PyClass outerClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
            assert node != null;
            if (outerClass != null && StringUtil.equalsIgnoreCase("meta", node.getName())) {
                return;
            }
            List<PyClassLikeType> types = node.getAncestorTypes(myTypeEvalContext);
            for (PyClassLikeType type : types) {
                if (type == null) {
                    return;
                }
                String qName = type.getClassQName();
                if (qName != null && qName.contains(PyNames.TEST_CASE)) {
                    return;
                }
                if (!(type instanceof PyClassType)) {
                    return;
                }
            }

            PyFunction init = node.findInitOrNew(true, null);
            if (init == null) {
                registerProblem(
                    node.getNameIdentifier(),
                    PyLocalize.inspClassHasNoInit().get(),
                    new AddMethodQuickFix("__init__", node.getName(), false)
                );
            }
        }
    }
}