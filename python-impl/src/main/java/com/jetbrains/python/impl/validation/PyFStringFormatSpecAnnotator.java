// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpec;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecComponent;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecComponentKind;
import com.jetbrains.python.impl.psi.PyStringDunderUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotator for f-string format specifications (PY-88215).
 * <p>
 * Highlights format spec components based on the type of the formatted expression:
 * - Numeric types (int, float): dots, numbers, format type chars (b,d,e,f,g,o,x,%)
 * - String types: alignment chars, numbers, format type char (s)
 * <p>
 * Examples:
 * - f"{f:.2f}" where f: float - highlights . 2 f
 * - f"{s:>10s}" where s: str - highlights > 1 0 s
 * - f"{x:.2f}" where x has unknown type - does NOT highlight
 */
public class PyFStringFormatSpecAnnotator extends PyAnnotatorBase {
    @Override
    protected void annotate(PsiElement element, PyAnnotationHolder holder) {
        if (holder.isBatchMode()) {
            return;
        }
        element.accept(new PyFStringFormatSpecVisitor(holder));
    }

    private static class PyFStringFormatSpecVisitor extends PyElementVisitor {
        private static final Set<String> DATETIME_TYPE_NAMES = Set.of(
            PyNames.FQN.DATETIME, PyNames.FQN.DATE, PyNames.FQN.TIME
        );

        private final PyAnnotationHolder myHolder;

        private PyFStringFormatSpecVisitor(PyAnnotationHolder holder) {
            myHolder = holder;
        }

        @Override
        public void visitPyFStringFragment(PyFStringFragment fragment) {
            PsiElement formatPart = fragment.getFormatPart();
            if (formatPart == null) {
                return;
            }
            PyExpression expression = fragment.getExpression();
            if (expression == null) {
                return;
            }

            // Check for type conversion (!s, !r, !a)
            PsiElement typeConversion = fragment.getTypeConversion();
            ExpressionType expressionType;
            if (typeConversion != null) {
                // Type conversion modifiers (!s, !r, !a) all convert to string
                expressionType = ExpressionType.STRING_OR_NUMERIC;
            }
            // No conversion - use the actual expression type
            else {
                expressionType = getExpressionType(expression);
            }

            if (expressionType == ExpressionType.UNKNOWN) {
                return;
            }

            // Highlight format spec components based on type
            highlightFormatSpec(formatPart, expressionType);
        }

        private enum ExpressionType {
            STRING_OR_NUMERIC, // str, int, float, complex numpy
            DATETIME, // datetime, date, time
            UNKNOWN, // type cannot be determined or is not a standard type
        }

        private static ExpressionType getExpressionType(PyExpression expression) {
            TypeEvalContext context = TypeEvalContext.codeAnalysis(expression.getProject(), expression.getContainingFile());
            PyType type = context.getType(expression);
            if (type == null) {
                return ExpressionType.UNKNOWN;
            }

            Set<@Nullable String> memberNames = new HashSet<>();
            for (PyType component : PyTypeUtil.asUnionSequence(type)) {
                if (!(component instanceof PyClassType classType) || classType.isDefinition()) {
                    continue;
                }
                for (PyClassLikeType superClassType : classType.getSuperClassTypes(context)) {
                    memberNames.add(superClassType != null ? superClassType.getClassQName() : null);
                }
                memberNames.add(classType.getClassQName());
            }

            if (memberNames.stream().anyMatch(PyStringDunderUtil.KNOWN_FORMAT_MINI_LANGUAGE_TYPES::contains)) {
                return ExpressionType.STRING_OR_NUMERIC;
            }
            else if (memberNames.stream().anyMatch(DATETIME_TYPE_NAMES::contains)) {
                return ExpressionType.DATETIME;
            }
            else {
                return ExpressionType.UNKNOWN;
            }
        }

        private void highlightFormatSpec(PsiElement formatPart, ExpressionType expressionType) {
            // Find the start of the format spec content (after the colon)
            ASTNode[] children = formatPart.getNode().getChildren(null);
            PsiElement formatSpecStart = null;
            for (ASTNode child : children) {
                if (child.getElementType() == PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START) {
                    formatSpecStart = child.getPsi().getNextSibling();
                    break;
                }
            }

            if (formatSpecStart == null) {
                return;
            }

            // Traverse the format spec and highlight appropriate components
            PsiElement current = formatSpecStart;
            while (current != null && current.getParent() == formatPart) {
                if (current.getNode().getElementType() == PyTokenTypes.FSTRING_TEXT
                    || current.getNode().getElementType() == PyTokenTypes.FSTRING_RAW_TEXT) {
                    highlightFormatSpecComponents(current, current.getText(), expressionType);
                }
                current = current.getNextSibling();
            }
        }

        /**
         * Highlights the components that {@link PyFormatSpec} recognizes in {@code text}. The parser is shared with the
         * completion contributor and the documentation providers, so all three read a character the same way.
         * The policy stays here: highlighting follows the syntax, and an invalid combination is reported by an
         * inspection instead.
         */
        private void highlightFormatSpecComponents(PsiElement element, String text, ExpressionType expressionType) {
            int baseOffset = element.getTextRange().getStartOffset();
            PyFormatSpec spec = PyFormatSpec.parse(text, expressionType == ExpressionType.DATETIME);

            for (PyFormatSpecComponent component : spec.getComponents()) {
                PyFormatSpecComponentKind kind = component.getKind();
                // A fill character carries no meaning of its own, so it stays unhighlighted.
                if (kind == PyFormatSpecComponentKind.FILL) {
                    continue;
                }
                // A width or a precision is a run of digits, highlighted one digit at a time.
                else if (kind == PyFormatSpecComponentKind.WIDTH || kind == PyFormatSpecComponentKind.PRECISION) {
                    for (int offset = component.getStartOffset(); offset < component.getEndOffset(); offset++) {
                        highlight(new TextRange(baseOffset + offset, baseOffset + offset + 1), PyHighlighter.PY_FSTRING_FORMAT_SPEC_NUMBER);
                    }
                }
                else {
                    highlight(new TextRange(baseOffset + component.getStartOffset(), baseOffset + component.getEndOffset()),
                        PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR);
                }
            }
        }

        private void highlight(TextRange range, TextAttributesKey attributes) {
            myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(attributes)
                .create();
        }
    }
}
