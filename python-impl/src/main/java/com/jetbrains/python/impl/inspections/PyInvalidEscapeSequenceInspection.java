// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyConvertToRawStringQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyEscapeBackslashQuickFix;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

@ExtensionImpl
public class PyInvalidEscapeSequenceInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInvalidEscapeSequence();
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
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
            for (PyStringElement it : node.getStringElements()) {
                processString(it, node);
            }
        }

        private void processString(PyStringElement element, PyStringLiteralExpression node) {
            if (element.getPrefix().toLowerCase(Locale.ROOT).contains("r")) {
                return;
            }

            String content = element.getContentRange().substring(element.getText());
            int offsetInNode = element.getTextRange().getStartOffset() + element.getContentRange().getStartOffset()
                - node.getTextRange().getStartOffset();

            List<TextRange> fstringRanges = new ArrayList<>();
            if (element instanceof PyFormattedStringElement formattedStringElement) {
                for (TextRange it : formattedStringElement.getLiteralPartRanges()) {
                    fstringRanges.add(it.shiftLeft(element.getContentRange().getStartOffset()));
                }
            }
            else {
                fstringRanges.add(new TextRange(0, content.length()));
            }

            // Ranges of valid escapes, as inclusive [first, last] pairs
            List<int[]> validEscapeList = new ArrayList<>();
            for (TextRange range : fstringRanges) {
                Matcher matcher = PyStringLiteralDecoder.PATTERN_ESCAPE.matcher(content);
                int from = range.getStartOffset();
                while (from <= content.length() && matcher.find(from)) {
                    int first = matcher.start();
                    int last = matcher.end() - 1;
                    if (!(last < range.getEndOffset())) {
                        break;
                    }
                    validEscapeList.add(new int[]{first, last});
                    // Kotlin Regex.findAll: an empty match advances by one character
                    from = matcher.end() == matcher.start() ? matcher.end() + 1 : matcher.end();
                }
            }
            Iterator<int[]> validEscapes = validEscapeList.iterator();
            boolean hasValidEscapes = validEscapes.hasNext();
            int @Nullable [] curValidEscape = hasValidEscapes ? validEscapes.next() : null;

            for (TextRange range : fstringRanges) {
                int charIndex = range.getStartOffset();
                while (charIndex < range.getEndOffset() - 1) {

                    if (content.charAt(charIndex) != '\\') {
                        charIndex++;
                        continue;
                    }

                    while (curValidEscape != null && curValidEscape[1] < charIndex) {
                        curValidEscape = validEscapes.hasNext() ? validEscapes.next() : null;
                    }
                    if (curValidEscape != null && curValidEscape[0] <= charIndex && charIndex <= curValidEscape[1]) {
                        charIndex = curValidEscape[1] + 1;
                        continue;
                    }

                    int problemOffset = offsetInNode + charIndex;

                    LocalQuickFix[] fixes = hasValidEscapes
                        ? new LocalQuickFix[]{new PyEscapeBackslashQuickFix(problemOffset)}
                        : new LocalQuickFix[]{new PyEscapeBackslashQuickFix(problemOffset), new PyConvertToRawStringQuickFix(problemOffset)};

                    registerProblem(
                        node,
                        PyLocalize.inspInvalidEscapeSequence("\\" + content.charAt(charIndex + 1)).get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        null,
                        new TextRange(problemOffset, problemOffset + 2),
                        fixes
                    );
                    charIndex += 2;
                }
            }
        }
    }
}
