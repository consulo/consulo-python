// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.util.IncorrectOperationException;

/**
 * Declared in {@code DjangoRegexpFile.kt}. JB registers it outside of the Python community plugin.
 */
public class DjangoRegexpUrlPathFileManipulator extends AbstractElementManipulator<DjangoRegexpFile> {
    @Override
    public DjangoRegexpFile handleContentChange(DjangoRegexpFile file, TextRange range, String newContent)
        throws IncorrectOperationException {
        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        document.replaceString(range.getStartOffset(), range.getEndOffset(), newContent);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        return file;
    }

    @Override
    public Class<DjangoRegexpFile> getElementClass() {
        return DjangoRegexpFile.class;
    }
}
