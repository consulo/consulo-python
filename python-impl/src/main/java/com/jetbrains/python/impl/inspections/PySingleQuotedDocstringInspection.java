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

import com.jetbrains.python.impl.inspections.quickfix.ConvertDocstringQuickFix;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Inspection to detect docstrings not using triple double-quoted string
 *
 * @author catherine
 */
@ExtensionImpl
public class PySingleQuotedDocstringInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameSingleQuotedDocstring();
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
        public void visitPyStringLiteralExpression(PyStringLiteralExpression string) {
            String stringText = string.getText();
            int length = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
            stringText = stringText.substring(length);
            PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
            if (docStringOwner != null) {
                if (docStringOwner.getDocStringExpression() == string) {
                    if (!stringText.startsWith("\"\"\"") && !stringText.endsWith("\"\"\"")) {
                        ProblemsHolder holder = getHolder();
                        if (holder != null) {
                            int quoteCount = 1;
                            if (stringText.startsWith("'''") && stringText.endsWith("'''")) {
                                quoteCount = 3;
                            }
                            TextRange trStart = new TextRange(length, length + quoteCount);
                            TextRange trEnd = new TextRange(
                                stringText.length() + length - quoteCount,
                                stringText.length() + length
                            );
                            if (string.getStringValue().isEmpty()) {
                                holder.newProblem(PyLocalize.inspMessageSingleQuotedDocstring())
                                    .range(string)
                                    .withFix(new ConvertDocstringQuickFix())
                                    .create();
                            }
                            else {
                                holder.newProblem(PyLocalize.inspMessageSingleQuotedDocstring())
                                    .range(string, trStart)
                                    .withFix(new ConvertDocstringQuickFix())
                                    .create();
                                holder.newProblem(PyLocalize.inspMessageSingleQuotedDocstring())
                                    .range(string, trEnd)
                                    .withFix(new ConvertDocstringQuickFix())
                                    .create();
                            }
                        }
                    }
                }
            }
        }
    }
}
