// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import static com.jetbrains.python.psi.FutureFeature.UNICODE_LITERALS;

@ExtensionImpl
public final class PyByteLiteralInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameByteLiteral();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
            String value = node.getStringValue();
            PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
            if (file == null) {
                return;
            }
            boolean default_bytes = false;
            if (file instanceof PyFile pyfile) {
                default_bytes = (!UNICODE_LITERALS.requiredAt(pyfile.getLanguageLevel()) &&
                    !pyfile.hasImportFromFuture(UNICODE_LITERALS)
                );
            }

            String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(file);
            try {
                if (charsetString != null && !Charset.forName(charsetString).equals(StandardCharsets.US_ASCII)) {
                    default_bytes = false;
                }
            }
            catch (UnsupportedCharsetException | IllegalCharsetNameException ignored) {
            }

            boolean hasNonAscii = false;

            int length = value.length();
            char c;
            for (int i = 0; i < length; ++i) {
                c = value.charAt(i);
                if (((int) c) > 255) {
                    hasNonAscii = true;
                    break;
                }
            }

            char first_char = Character.toLowerCase(node.getText().charAt(0));
            boolean isByte = first_char == 'b' || (default_bytes && first_char != 'u');

            if (hasNonAscii && isByte) {
                registerProblem(node, PyLocalize.inspByteLiteralContainsIllegalCharacters().get());
            }
        }
    }
}
