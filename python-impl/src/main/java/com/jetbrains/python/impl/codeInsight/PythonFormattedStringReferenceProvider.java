// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.List;

public class PythonFormattedStringReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
        if (PythonFormattedStringReferenceContributor.Holder.FORMAT_STRING_PATTERN.accepts(element)) {
            return getReferencesFromFormatString((PyStringLiteralExpression)element);
        }
        else {
            return getReferencesFromPercentString((PyStringLiteralExpression)element);
        }
    }

    private static PySubstitutionChunkReference[] getReferencesFromFormatString(PyStringLiteralExpression element) {
        List<PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.filterSubstitutions(
            PyStringFormatParser.parseNewStyleFormat(element.getText()));
        return getReferencesFromChunks(element, chunks);
    }

    private static PySubstitutionChunkReference[] getReferencesFromPercentString(PyStringLiteralExpression element) {
        List<PyStringFormatParser.SubstitutionChunk>
            chunks = PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(element.getText()));
        return getReferencesFromChunks(element, chunks);
    }

    public static PySubstitutionChunkReference[] getReferencesFromChunks(PyStringLiteralExpression element,
        List<? extends PyStringFormatParser.SubstitutionChunk> chunks) {
        return ContainerUtil.map2Array(chunks, PySubstitutionChunkReference.class, chunk -> new PySubstitutionChunkReference(element, chunk));
    }
}
