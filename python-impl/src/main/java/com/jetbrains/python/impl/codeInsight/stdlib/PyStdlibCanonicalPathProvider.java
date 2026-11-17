// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.sdk.skeleton.PySkeletonUtil;
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.QualifiedName;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PyStdlibCanonicalPathProvider implements PyCanonicalPathProvider {
    @Override
    public @Nullable QualifiedName getCanonicalPath(@Nullable PsiElement symbol, QualifiedName qName, @Nullable PsiElement foothold) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(symbol);
        if (virtualFile != null && foothold != null) {
            QualifiedName canonicalPath = restoreStdlibCanonicalPath(qName);
            if (canonicalPath != null && PySkeletonUtil.isStdLib(virtualFile, MainPythonSdkKt.pythonSdkIfReady(foothold))) {
                return canonicalPath;
            }
        }
        return null;
    }

    private static QualifiedName replaceHead(QualifiedName qName, String newHead) {
        return QualifiedName.fromDottedString(newHead).append(qName.removeHead(1));
    }

    public static @Nullable QualifiedName restoreStdlibCanonicalPath(QualifiedName qName) {
        if (qName.matchesPrefix(QualifiedName.fromComponents("mock", "mock"))) {
            return qName.removeHead(1);
        }
        String firstComponent = qName.getFirstComponent();
        if (firstComponent == null) {
            return null;
        }
        return switch (firstComponent) {
            case "_abcoll", "_collections" -> replaceHead(qName, "collections");
            case "_collections_abc" -> replaceHead(qName, "collections.abc");
            case "posix", "nt" -> replaceHead(qName, "os");
            case "_functools" -> replaceHead(qName, "functools");
            case "_struct" -> replaceHead(qName, "struct");
            case "_io", "_pyio", "_fileio" -> replaceHead(qName, "io");
            case "_datetime" -> replaceHead(qName, "datetime");
            case "ntpath", "posixpath", "path", "macpath", "os2emxpath", "genericpath" -> replaceHead(qName, "os.path");
            case "_sqlite3" -> replaceHead(qName, "sqlite3");
            case "_pickle" -> replaceHead(qName, "pickle");
            case "_decimal" -> replaceHead(qName, "decimal");
            default -> null;
        };
    }
}
