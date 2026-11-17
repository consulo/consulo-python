// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.inlay.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides inlay hints for Python numeric type promotions.
 * <p>
 * In Python's type system, `float` accepts `int` values, and `complex` accepts both `float` and `int` values.
 * This provider shows hints to indicate these implicit numeric promotions.
 * <p>
 * For example:
 * - `a: float` shows ` | int` hint
 * - `a: complex` shows ` | float | int` hint
 * - `a: float | int` shows no hint (already explicit)
 * - `list[float]` shows ` | int` hint after `float`
 */
@ExtensionImpl
public class PyNumericPromotionInlayHintsProvider implements DeclarativeInlayHintsProvider {
    private static final String FLOAT_PROMOTION_HINT = " | float";
    private static final String INT_PROMOTION_HINT = " | int";
    private static final String FLOAT_INT_PROMOTION_HINT = FLOAT_PROMOTION_HINT + INT_PROMOTION_HINT;

    @Override
    public DeclarativeInlayHintsCollector createCollector(PsiFile file, Editor editor) {
        return new Collector();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @Override
    public String getId() {
        return "python.numeric.promotion.inlays";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.inlayNumericPromotion();
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.empty();
    }

    @Override
    public LocalizeValue getPreviewFileText() {
        // resources/inlayProviders/python.numeric.promotion.inlays/preview.py
        return LocalizeValue.of("""
            # Numeric type promotion hints show implicit type widening
            def calculate(
                value: float/*<#  | int#>*/,
                result: complex/*<#  | float | int#>*/,
            ):
                pass


            # No hints when union already includes promoted types
            def explicit_unions(
                a: float | int,
                b: complex | float | int,
            ):
                pass
            """);
    }

    @Override
    public InlayGroup getGroup() {
        return InlayGroup.TYPES_GROUP;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    private static class Collector implements DeclarativeInlayHintsCollector.SharedBypassCollector {
        private final HintFormat hintFormat = HintFormat.DEFAULT;

        @Override
        public void collectFromElement(PsiElement element, DeclarativeInlayTreeSink sink) {
            if (!(element instanceof PyReferenceExpression referenceExpression)) {
                return;
            }

            if (!isInTypeContext(referenceExpression)) {
                return;
            }

            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
            PyClassType floatType = builtinCache.getFloatType();
            PyClass floatClass = floatType != null ? floatType.getPyClass() : null;
            if (floatClass == null) {
                return;
            }
            PyClassType complexType = builtinCache.getComplexType();
            PyClass complexClass = complexType != null ? complexType.getPyClass() : null;
            if (complexClass == null) {
                return;
            }
            PyClassType intType = builtinCache.getIntType();
            PyClass intClass = intType != null ? intType.getPyClass() : null;
            if (intClass == null) {
                return;
            }

            PsiElement resolved = referenceExpression.getReference().resolve();

            String promotionHint;
            if (complexClass.equals(resolved)) {
                promotionHint = getComplexPromotionHint(referenceExpression, floatClass, intClass);
            }
            else if (floatClass.equals(resolved)) {
                promotionHint = getFloatPromotionHint(referenceExpression, intClass);
            }
            else {
                promotionHint = null;
            }
            if (promotionHint == null) {
                return;
            }

            sink.addPresentation(
                new DeclarativeInlayPosition.InlineInlayPosition(element.getTextRange().getEndOffset(), true),
                null,
                "Implicit numeric promotion",
                hintFormat,
                builder -> builder.text(promotionHint)
            );
        }

        private static boolean isInTypeContext(PyReferenceExpression expression) {
            for (PsiElement p = expression.getParent(); p != null; p = p.getParent()) {
                if (p instanceof PyAnnotation || p instanceof PyTypeAliasStatement) {
                    return true;
                }
            }
            if (isGenericSubscriptArgument(expression)) {
                return true;
            }
            PyTypeParameter typeParam = PsiTreeUtil.getParentOfType(expression, PyTypeParameter.class, true);
            if (typeParam == null) {
                return false;
            }
            return PsiTreeUtil.isAncestor(typeParam.getBoundExpression(), expression, false) ||
                PsiTreeUtil.isAncestor(typeParam.getDefaultExpression(), expression, false);
        }

        private static boolean isGenericSubscriptArgument(PyReferenceExpression expression) {
            PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(expression, PySubscriptionExpression.class, true);
            if (subscription == null) {
                return false;
            }
            PyExpression flatIndex = PyPsiUtils.flattenParens(subscription.getIndexExpression());
            if (flatIndex == null) {
                return false;
            }
            if (flatIndex instanceof PyTupleExpression) {
                if (expression.getParent() != flatIndex) {
                    return false;
                }
            }
            else {
                if (expression != flatIndex) {
                    return false;
                }
            }
            TypeEvalContext context = TypeEvalContext.codeAnalysis(expression.getProject(), expression.getContainingFile());
            PyType operandType = context.getType(subscription.getOperand());
            return operandType instanceof PyClassType classType &&
                classType.isDefinition() &&
                PyTypingTypeProvider.isGeneric(classType, context);
        }

        /**
         * Gets the promotion hint for `complex` type.
         * Returns " | float | int", " | float", " | int", or null depending on
         * what's already present in the containing union.
         */
        private static @Nullable String getComplexPromotionHint(PyReferenceExpression expression, PyClass floatClass, PyClass intClass) {
            List<PsiElement> unionMembers = getUnionSiblings(expression);
            if (unionMembers.isEmpty()) {
                return FLOAT_INT_PROMOTION_HINT;
            }

            boolean hasFloat = unionMembers.stream().anyMatch(it -> isReferenceToClass(it, floatClass));
            boolean hasInt = unionMembers.stream().anyMatch(it -> isReferenceToClass(it, intClass));

            if (!hasFloat && !hasInt) {
                return FLOAT_INT_PROMOTION_HINT;
            }
            if (hasFloat && !hasInt) {
                return INT_PROMOTION_HINT;
            }
            if (!hasFloat && hasInt) {
                return FLOAT_PROMOTION_HINT;
            }
            return null;
        }

        /**
         * Gets the promotion hint for `float` type.
         * Returns " | int" or null depending on what's already present in the containing union.
         */
        private static @Nullable String getFloatPromotionHint(PyReferenceExpression expression, PyClass intClass) {
            List<PsiElement> unionMembers = getUnionSiblings(expression);
            if (unionMembers.isEmpty()) {
                return INT_PROMOTION_HINT;
            }

            boolean hasInt = unionMembers.stream().anyMatch(it -> isReferenceToClass(it, intClass));
            return !hasInt ? INT_PROMOTION_HINT : null;
        }

        /**
         * Gets all sibling members in the same union as the given element.
         * This handles both `X | Y` syntax and `Union[X, Y]` syntax.
         */
        private static List<PsiElement> getUnionSiblings(PyReferenceExpression expression) {
            PsiElement parent = expression.getParent();

            if (parent instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
                List<PsiElement> result = new ArrayList<>();
                for (PsiElement member : collectAllUnionMembers(findTopLevelUnion(binaryExpression))) {
                    if (member != expression) {
                        result.add(member);
                    }
                }
                return result;
            }

            if (parent instanceof PyTupleExpression tupleExpression) {
                if (tupleExpression.getParent() instanceof PySubscriptionExpression subscription) {
                    TypeEvalContext context = TypeEvalContext.codeAnalysis(expression.getProject(), expression.getContainingFile());
                    Collection<String> resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(subscription.getOperand(), context);
                    if (resolvedNames.contains(PyTypingTypeProvider.UNION)) {
                        List<PsiElement> result = new ArrayList<>();
                        for (PyExpression element : tupleExpression.getElements()) {
                            if (element != expression) {
                                result.add(element);
                            }
                        }
                        return result;
                    }
                }
            }

            return List.of();
        }

        /**
         * Finds the top-level binary expression in a chain of | operators.
         */
        private static PyBinaryExpression findTopLevelUnion(PyBinaryExpression expression) {
            PyBinaryExpression last = expression;
            for (PsiElement p = expression; p instanceof PyBinaryExpression binary && binary.getOperator() == PyTokenTypes.OR;
                 p = p.getParent()) {
                last = binary;
            }
            return last;
        }

        /**
         * Collects all members from a union binary expression tree.
         */
        private static List<PsiElement> collectAllUnionMembers(PsiElement element) {
            if (element instanceof PyBinaryExpression binaryExpression) {
                PyExpression left = binaryExpression.getLeftExpression();
                PyExpression right = binaryExpression.getRightExpression();
                if (left != null && right != null) {
                    List<PsiElement> result = new ArrayList<>(collectAllUnionMembers(left));
                    result.addAll(collectAllUnionMembers(right));
                    return result;
                }
                else {
                    return List.of();
                }
            }
            return List.of(element);
        }

        private static boolean isReferenceToClass(PsiElement element, PyClass pyClass) {
            if (!(element instanceof PyReferenceExpression referenceExpression)) {
                return false;
            }
            PsiElement resolved = referenceExpression.getReference().resolve();
            return pyClass.equals(resolved);
        }
    }
}
