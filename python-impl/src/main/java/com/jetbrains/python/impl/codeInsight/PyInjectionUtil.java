// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.language.Language;
import consulo.language.inject.MultiHostRegistrar;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.impl.PyStringFormatParser.SubstitutionChunk;
import static com.jetbrains.python.impl.PyStringFormatParser.parseNewStyleFormat;
import static com.jetbrains.python.impl.PyStringFormatParser.parsePercentFormat;

public final class PyInjectionUtil {

    public static class InjectionResult {
        public static final InjectionResult EMPTY = new InjectionResult(false, true);

        private final boolean myInjected;
        private final boolean myStrict;

        public InjectionResult(boolean injected, boolean strict) {
            myInjected = injected;
            myStrict = strict;
        }

        public boolean isInjected() {
            return myInjected;
        }

        public boolean isStrict() {
            return myStrict;
        }

        public InjectionResult append(InjectionResult result) {
            return new InjectionResult(myInjected || result.isInjected(), myStrict && result.isStrict());
        }
    }

    public static final List<Class<? extends PsiElement>> ELEMENTS_TO_INJECT_IN =
        List.of(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
                PyCallExpression.class, PsiComment.class);

    private PyInjectionUtil() {}

    /**
      * Returns the largest expression in the specified context that represents a string literal suitable for language injection, possibly
      * with concatenation, parentheses, or formatting.
      */
    public static @Nullable PsiElement getLargestStringLiteral(PsiElement context) {
        PsiElement element = null;
        for (PsiElement current = context; current != null && isStringLiteralPart(current, element); current = current.getParent()) {
            element = current;
        }
        return element;
    }

    /**
      * Registers language injections in the given registrar for the specified string literal element or its ancestor that contains
      * string concatenations or formatting.
      */
    public static InjectionResult registerStringLiteralInjection(PsiElement element,
                                                                          MultiHostRegistrar registrar,
                                                                          Language language) {
        return registerStringLiteralInjection(element, registrar, language, "", "");
    }

    /**
      * Registers language injections like {@link #registerStringLiteralInjection} but wraps the entire injected range with parentheses.
      * This is useful when the injected fragment must be implicitly parenthesized like a type annotation string injection.
      */
    public static InjectionResult registerStringLiteralInjectionWithParenthesis(PsiElement element,
                                                                          MultiHostRegistrar registrar,
                                                                          Language language) {
        return registerStringLiteralInjection(element, registrar, language, "(", ")");
    }

    private static InjectionResult registerStringLiteralInjection(PsiElement element,
                                                                          MultiHostRegistrar registrar,
                                                                          Language language,
                                                                          String prefix,
                                                                          String suffix) {
        registrar.startInjecting(language);
        InjectionResult result = processStringLiteral(element, registrar, prefix, suffix, Formatting.NONE);
        if (result.isInjected()) {
            // TODO consulo: MultiHostRegistrar.frankensteinInjection(boolean) missing,
            //  JB: registrar.frankensteinInjection(!result.isStrict()).doneInjecting()
            registrar.doneInjecting();
        }
        return result;
    }

    private static boolean isStringLiteralPart(PsiElement element, @Nullable PsiElement context) {
        if (element == context || element instanceof PyStringLiteralExpression || element instanceof PsiComment) {
            return true;
        }
        else if (element instanceof PyParenthesizedExpression) {
            PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
            return contained != null && isStringLiteralPart(contained, context);
        }
        else if (element instanceof PyBinaryExpression expr) {
            PyExpression left = expr.getLeftExpression();
            PyExpression right = expr.getRightExpression();
            if (expr.isOperator("+")) {
                return isStringLiteralPart(left, context) || right != null && isStringLiteralPart(right, context);
            }
            else if (expr.isOperator("%")) {
                return right != context && isStringLiteralPart(left, context);
            }
            return false;
        }
        else if (element instanceof PyCallExpression) {
            PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
            return qualifier != null && isStringLiteralPart(qualifier, context);
        }
        else if (element instanceof PyReferenceExpression) {
            PyCallExpression callExpr = PyCallExpressionNavigator.getPyCallExpressionByCallee(element);
            return callExpr != null && isStringLiteralPart(callExpr, context);
        }
        return false;
    }

    private static @Nullable PyExpression getFormatCallQualifier(PyCallExpression element) {
        PyExpression callee = element.getCallee();
        if (callee instanceof PyQualifiedExpression qualifiedExpr) {
            PyExpression qualifier = qualifiedExpr.getQualifier();
            if (qualifier != null && PyNames.FORMAT.equals(qualifiedExpr.getReferencedName())) {
                return qualifier;
            }
        }
        return null;
    }

    private static InjectionResult processStringLiteral(PsiElement element, MultiHostRegistrar registrar,
                                                                 String prefix, String suffix, Formatting formatting) {
        String missingValue = "missing_value";
        if (element instanceof PyStringLiteralExpression expr) {
            boolean injected = false;
            boolean strict = true;
            for (PyStringElement stringElem : expr.getStringElements()) {
                int nodeOffsetInParent = stringElem.getTextOffset() - expr.getTextRange().getStartOffset();
                TextRange contentRange = stringElem.getContentRange();
                int contentStartOffset = contentRange.getStartOffset();
                if (formatting != Formatting.NONE || stringElem.isFormatted() || stringElem.isTemplate()) {
                    // Each range is relative to the start of the string element
                    List<TextRange> subsRanges;
                    if (formatting != Formatting.NONE) {
                        String content = stringElem.getContent();
                        subsRanges = (formatting == Formatting.NEW_STYLE ? parseNewStyleFormat(content) : parsePercentFormat(content))
                            .stream()
                            .filter(SubstitutionChunk.class::isInstance)
                            .map(SubstitutionChunk.class::cast)
                            .map(chunk -> chunk.getTextRange().shiftRight(contentStartOffset))
                            .toList();
                    }
                    else {
                        subsRanges = ContainerUtil.map(((PyFormattedStringElement)stringElem).getFragments(), PsiElement::getTextRangeInParent);
                    }
                    if (!subsRanges.isEmpty()) {
                        strict = false;
                    }

                    TextRange sentinel = TextRange.from(contentRange.getEndOffset(), 0);
                    List<TextRange> withSentinel = new ArrayList<>(subsRanges);
                    withSentinel.add(sentinel);

                    int literalChunkStart = contentStartOffset;
                    int literalChunkEnd;
                    for (int i = 0; i < withSentinel.size(); i++) {
                        TextRange subRange = withSentinel.get(i);
                        literalChunkEnd = subRange.getStartOffset();
                        if (literalChunkEnd > literalChunkStart) {
                            String chunkPrefix;
                            if (i == 0) {
                                chunkPrefix = prefix;
                            }
                            else if (i == 1 && withSentinel.get(0).getStartOffset() == contentStartOffset) {
                                chunkPrefix = missingValue;
                            }
                            else {
                                chunkPrefix = "";
                            }

                            String chunkSuffix;
                            if (i < withSentinel.size() - 1) {
                                chunkSuffix = missingValue;
                            }
                            else if (i == withSentinel.size() - 1) {
                                chunkSuffix = suffix;
                            }
                            else {
                                chunkSuffix = "";
                            }

                            TextRange chunkRange = TextRange.create(literalChunkStart, literalChunkEnd);
                            registrar.addPlace(chunkPrefix, chunkSuffix, expr, chunkRange.shiftRight(nodeOffsetInParent));
                            injected = true;
                        }
                        literalChunkStart = subRange.getEndOffset();
                    }
                }
                else {
                    registrar.addPlace(prefix, suffix, expr, contentRange.shiftRight(nodeOffsetInParent));
                    injected = true;
                }
            }
            return new InjectionResult(injected, strict);
        }
        else if (element instanceof PyParenthesizedExpression) {
            PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
            if (contained != null) {
                return processStringLiteral(contained, registrar, prefix, suffix, formatting);
            }
        }
        else if (element instanceof PyBinaryExpression expr) {
            PyExpression left = expr.getLeftExpression();
            PyExpression right = expr.getRightExpression();
            boolean isLeftString = isStringLiteralPart(left, null);
            if (expr.isOperator("+")) {
                boolean isRightString = right != null && isStringLiteralPart(right, null);
                InjectionResult result = InjectionResult.EMPTY;
                if (isLeftString) {
                    result = result.append(processStringLiteral(left, registrar, prefix, isRightString ? "" : missingValue, formatting));
                }
                if (isRightString) {
                    result = result.append(processStringLiteral(right, registrar, isLeftString ? "" : missingValue, suffix, formatting));
                }
                return result;
            }
            else if (expr.isOperator("%")) {
                return processStringLiteral(left, registrar, prefix, suffix, Formatting.PERCENT);
            }
        }
        else if (element instanceof PyCallExpression) {
            PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
            if (qualifier != null) {
                return processStringLiteral(qualifier, registrar, prefix, suffix, Formatting.NEW_STYLE);
            }
        }
        return InjectionResult.EMPTY;
    }

    private enum Formatting {
        NONE,
        PERCENT,
        NEW_STYLE
    }
}
