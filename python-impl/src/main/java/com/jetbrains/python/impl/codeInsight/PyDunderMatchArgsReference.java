// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.PyRemoveElementFix;
import com.jetbrains.python.impl.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Reference for names listed in __match_args__ tuples.
 * <p>
 * Similar to {@link PyDunderSlotsReference}, but resolves using {@link PyType#resolveMember}
 * to account for properties, descriptors, class members, etc.
 */
public class PyDunderMatchArgsReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
    public PyDunderMatchArgsReference(PyStringLiteralExpression element) {
        super(element, firstOrNull(element.getStringValueTextRanges()));
    }

    private static @Nullable TextRange firstOrNull(List<TextRange> ranges) {
        return ranges.isEmpty() ? null : ranges.get(0);
    }

    @Override
    public @Nullable PsiElement resolve() {
        PyClass referenceClass = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
        if (referenceClass == null) {
            return null;
        }
        TypeEvalContext typeContext = TypeEvalContext.codeAnalysis(myElement.getProject(), myElement.getContainingFile());

        PyClassLikeType type = referenceClass.getType(typeContext);
        if (type == null) {
            return null;
        }
        PyClassLikeType instance = type.toInstance();
        if (instance == null) {
            return null;
        }
        List<? extends RatedResolveResult> results =
            instance.resolveMember(myElement.getStringValue(), null, AccessDirection.READ, PyResolveContext.defaultContext(typeContext));
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.get(0).getElement();
    }

    @Override
    public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        return HighlightSeverity.WARNING;
    }

    @Override
    public @Nullable String getUnresolvedDescription() {
        return null;
    }

    @Override
    public List<LocalQuickFix> getQuickFixes(TypeEvalContext context) {
        PyClass clazz = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
        if (clazz == null) {
            return Collections.emptyList();
        }
        return List.of(
            new AddFieldQuickFix(myElement.getStringValue(), PyNames.NONE, clazz.getName(), true),
            // TODO consulo: LocalQuickFix.from(ModCommandAction) missing
            new PyRemoveElementFix(myElement)
        );
    }
}
