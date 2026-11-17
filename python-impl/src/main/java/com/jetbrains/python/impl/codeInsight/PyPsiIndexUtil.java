// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.impl.findUsages.PyPsiFindUsagesHandlerFactory;
import consulo.find.FindUsagesHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyPsiIndexUtil {
    public static List<UsageInfo> findUsages(PsiNamedElement element, boolean forHighlightUsages) {
        List<UsageInfo> usages = new ArrayList<>();
        FindUsagesHandler handler = new PyPsiFindUsagesHandlerFactory() {
        }.createFindUsagesHandler(element, forHighlightUsages);
        assert handler != null;
        List<PsiElement> elementsToProcess = new ArrayList<>();
        Collections.addAll(elementsToProcess, handler.getPrimaryElements());
        Collections.addAll(elementsToProcess, handler.getSecondaryElements());
        for (PsiElement e : elementsToProcess) {
            handler.processElementUsages(e, usageInfo -> {
                if (!usageInfo.isNonCodeUsage) {
                    usages.add(usageInfo);
                }
                return true;
            }, FindUsagesHandler.createFindUsagesOptions(element.getProject(), null));
        }
        return usages;
    }

    public static boolean isNotUnderSourceRoot(Project project, @Nullable PsiFile psiFile) {
        if (psiFile == null) {
            return true;
        }
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            if (fileIndex.isExcluded(virtualFile) || (fileIndex.isInLibraryClasses(virtualFile) && !fileIndex.isInContent(virtualFile))) {
                return true;
            }
        }
        return false;
    }
}
