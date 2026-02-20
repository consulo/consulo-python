/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author catherine
 */
@ExtensionImpl
public class PyMandatoryEncodingInspection extends PyInspection {
    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyMandatoryEncodingInspectionState();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMandatoryEncoding();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session, (PyMandatoryEncodingInspectionState) state);
    }

    private class Visitor extends PyInspectionVisitor {
        private final PyMandatoryEncodingInspectionState myState;

        public Visitor(
            @Nullable ProblemsHolder holder,
            @Nonnull LocalInspectionToolSession session,
            PyMandatoryEncodingInspectionState state
        ) {
            super(holder, session);
            myState = state;
        }

        @Override
        public void visitPyFile(PyFile node) {
            String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(node.getText());
            if (charsetString == null) {
                TextRange tr = new TextRange(0, 0);
                ProblemsHolder holder = getHolder();
                if (holder != null) {
                    holder.registerProblem(
                        node,
                        tr,
                        "No encoding specified for file",
                        new AddEncodingQuickFix(myState.myDefaultEncoding, myState.myEncodingFormatIndex)
                    );
                }
            }
        }
    }
}
