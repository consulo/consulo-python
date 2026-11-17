// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level declarations of {@code TypeIgnoreComments.kt}.
 */
final class TypeIgnoreComments {
    enum IgnoreScope {
        LINE,
        FILE
    }

    private TypeIgnoreComments() {
    }

    @RequiredReadAction
    static @Nullable IgnoreScope ignoreScope(PsiComment comment) {
        if (followsCodeOnItsLine(comment)) {
            return IgnoreScope.LINE;
        }
        PsiFile file = comment.getContainingFile();
        if (file == null) {
            return null;
        }
        for (PsiComment leading : leadingFileLevelComments(file)) {
            if (leading == comment) {
                return IgnoreScope.FILE;
            }
        }
        return null;
    }

    static List<PsiComment> leadingFileLevelComments(PsiFile file) {
        List<PsiComment> result = new ArrayList<>();
        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof PsiComment || child instanceof PsiWhiteSpace)) {
                break;
            }
            if (child instanceof PsiComment comment) {
                result.add(comment);
            }
        }
        return result;
    }

    @RequiredReadAction
    private static boolean followsCodeOnItsLine(PsiComment comment) {
        PsiElement previous = PsiTreeUtil.prevLeaf(comment);
        if (previous == null) {
            return false;
        }
        while (previous instanceof PsiWhiteSpace) {
            if (previous.getText().contains("\n")) {
                return false;
            }
            previous = PsiTreeUtil.prevLeaf(previous);
            if (previous == null) {
                return false;
            }
        }
        return !(previous instanceof PsiComment);
    }
}
