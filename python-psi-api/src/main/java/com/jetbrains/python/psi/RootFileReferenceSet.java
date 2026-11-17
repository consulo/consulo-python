// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.application.ApplicationManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.path.FileReferenceSet;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.ManagingFS;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Resolves absolute paths from FS root, not content roots
 *
 * @author traff
 */
public class RootFileReferenceSet extends FileReferenceSet {
    public RootFileReferenceSet(String str,
                                PsiElement element,
                                int startInElement,
                                @Nullable PsiReferenceProvider provider,
                                boolean caseSensitive,
                                boolean endingSlashNotAllowed,
                                FileType @Nullable [] suitableFileTypes) {
        super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes);
    }

    public RootFileReferenceSet(String s, PsiElement element, int offset, @Nullable PsiReferenceProvider provider, boolean sensitive) {
        super(s, element, offset, provider, sensitive);
    }

    @Override
    public boolean isAbsolutePathReference() {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            return FileUtil.isAbsolute(getPathString());
        }
        else {
            return super.isAbsolutePathReference();
        }
    }

    @Override
    public Collection<PsiFileSystemItem> computeDefaultContexts() {
        PsiFile file = getContainingFile();
        if (file == null) {
            return List.of();
        }

        if (isAbsolutePathReference() && !ApplicationManager.getApplication().isUnitTestMode()) {
            return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
        }

        return super.computeDefaultContexts();
    }
}
