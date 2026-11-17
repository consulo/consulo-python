// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.CompletionWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.logging.Logger;

/**
 * Weighs down items starting with two underscores.
 * <br/>
 */
@ExtensionImpl
public final class PythonCompletionWeigher extends CompletionWeigher {

    // TODO Unify different ways of detecting and weighing elements
    public static final int PRIORITY_WEIGHT = 5;
    public static final int WEIGHT_FOR_MULTIPLE_ARGUMENTS = 5;
    public static final int WEIGHT_FOR_KEYWORDS = 0;
    private static final Logger LOG = Logger.getInstance(PythonCompletionWeigher.class);
    public static final String COLLECTION_KEY = "dict key";
    private static final int COLLECTION_KEY_WEIGHT = 10;
    public static final int NOT_IMPORTED_MODULE_WEIGHT = -1;

    @Override
    public Comparable weigh(final LookupElement element, final CompletionLocation location) {
        if (!PsiUtilCore.findLanguageFromElement(location.getCompletionParameters().getPosition()).isKindOf(PythonLanguage.getInstance())) {
            return PyCompletionUtilsKt.FALLBACK_WEIGHT;
        }

        final String name = element.getLookupString();
        final LookupElementPresentation presentation = LookupElementPresentation.renderElement(element);
        // move dict keys to the top
        if (COLLECTION_KEY.equals(presentation.getTypeText())) {
            return COLLECTION_KEY_WEIGHT;
        }

        PsiElement psiElement = element.getPsiElement();
        PsiFile file = location.getCompletionParameters().getOriginalFile();
        if (psiElement != null) {
            if (psiElement.getContainingFile() == file) {
                return PRIORITY_WEIGHT;
            }

            PsiElement dummyParent = location.getCompletionParameters().getPosition().getParent();
            boolean isQualified = dummyParent instanceof PyReferenceExpression ref && ref.isQualified();
            int completionWeight = PyCompletionUtilsKt.computeCompletionWeight(psiElement, name, null, file, isQualified);
            LOG.debug("Combined weight for completion item " + name + ": " + completionWeight);
            return completionWeight;
        }

        if (PyNames.isReserved(element.getLookupString())) {
            return WEIGHT_FOR_KEYWORDS;
        }

        if (element.getUserData(PyMultipleArgumentsCompletionContributor.Helper.MULTIPLE_ARGUMENTS_VARIANT_KEY) != null) {
            return WEIGHT_FOR_MULTIPLE_ARGUMENTS;
        }

        return PyCompletionUtilsKt.FALLBACK_WEIGHT;
    }
}
