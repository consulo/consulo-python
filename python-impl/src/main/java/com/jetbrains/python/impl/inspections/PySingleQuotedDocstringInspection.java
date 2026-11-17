// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.ConvertDocstringQuickFix;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * Inspection to detect docstrings not using triple double-quoted string
 */
@ExtensionImpl
public final class PySingleQuotedDocstringInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameSingleQuotedDocstring();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonDocstrings();
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

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression string) {
            String stringText = string.getText();
            int length = PyStringLiteralUtil.getPrefixLength(stringText);
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
                            TextRange trEnd = new TextRange(stringText.length() + length - quoteCount,
                                                            stringText.length() + length);
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
