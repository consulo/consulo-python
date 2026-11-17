// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

/**
 * User : catherine
 */
@ExtensionImpl
public final class PyNonAsciiCharInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNonAscii();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyNonAsciiCharInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), (PyNonAsciiCharInspectionState) state);
    }

    private static class Visitor extends PyInspectionVisitor {
        private final PyNonAsciiCharInspectionState myState;

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context, PyNonAsciiCharInspectionState state) {
            super(holder, context);
            myState = state;
        }

        @Override
        public void visitComment(PsiComment node) {
            checkString(node, node.getText());
        }

        private void checkString(PsiElement node, String value) {
            if (LanguageLevel.forElement(node).isPython2()) {
                PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
                if (file == null) {
                    return;
                }
                String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(file);

                boolean hasNonAscii = false;

                CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
                int length = value.length();
                char c = 0;
                for (int i = 0; i < length; ++i) {
                    c = value.charAt(i);
                    if (!asciiEncoder.canEncode(c)) {
                        hasNonAscii = true;
                        break;
                    }
                }

                if (hasNonAscii) {
                    if (charsetString == null) {
                        registerProblem(
                            node,
                            PyInspectionMessages.problemMessage(a -> PyLocalize.inspNonAsciiCharNonAsciiCharacterInFileButNoEncodingDeclared(a[0]), c),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            new AddEncodingQuickFix(myState.myDefaultEncoding, myState.myEncodingFormatIndex)
                        );
                    }
                }
            }
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
            checkString(node, node.getText());
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            checkString(node, node.getText());
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            checkString(node, node.getText());
        }
    }
}
