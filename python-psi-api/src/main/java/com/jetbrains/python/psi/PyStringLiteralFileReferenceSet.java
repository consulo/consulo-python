// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.application.util.SystemInfo;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.path.FileReference;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralFileReferenceSet extends RootFileReferenceSet {
    public static final Pattern DELIMITERS = Pattern.compile("\\\\|/");
    private final PyStringLiteralExpression myStringLiteralExpression;


    public PyStringLiteralFileReferenceSet(PyStringLiteralExpression element) {
        // TODO consulo: VirtualFile.isCaseSensitive missing
        // JB: element.getContainingFile().getViewProvider().getVirtualFile().isCaseSensitive()
        this(element, SystemInfo.isFileSystemCaseSensitive);
    }

    public PyStringLiteralFileReferenceSet(PyStringLiteralExpression element, boolean caseSensitive) {
        this(element.getStringValue(), element, element.getStringValueTextRange().getStartOffset(), null, caseSensitive, true,
            FileType.EMPTY_ARRAY);
    }

    public PyStringLiteralFileReferenceSet(String str,
                                           PyStringLiteralExpression element,
                                           int startInElement,
                                           @Nullable PsiReferenceProvider provider,
                                           boolean caseSensitive, boolean endingSlashNotAllowed, FileType @Nullable [] suitableFileTypes) {
        super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed,
            suitableFileTypes);
        myStringLiteralExpression = element;
        reparse();
    }

    @Override
    protected void reparse() {
        //noinspection ConstantConditions
        if (myStringLiteralExpression != null) {
            List<FileReference> references = getFileReferences(myStringLiteralExpression);
            myReferences = references.toArray(FileReference.EMPTY);
        }
    }

    private List<FileReference> getFileReferences(PyStringLiteralExpression expression) {
        String value = expression.getStringValue();
        Matcher matcher = DELIMITERS.matcher(value);
        int start = 0;
        int index = 0;
        List<FileReference> results = new ArrayList<>();
        while (matcher.find()) {
            String s = value.substring(start, matcher.start());
            if (!s.isEmpty()) {
                TextRange range = TextRange.create(expression.valueOffsetToTextOffset(start),
                    expression.valueOffsetToTextOffset(matcher.start()));
                results.add(createFileReference(range, index++, s));
            }
            start = matcher.end();
        }
        String s = value.substring(start);
        if (!s.isEmpty()) {
            TextRange range = TextRange.create(expression.valueOffsetToTextOffset(start),
                expression.valueOffsetToTextOffset(value.length()));
            results.add(createFileReference(range, index, s));
        }
        return results;
    }
}
