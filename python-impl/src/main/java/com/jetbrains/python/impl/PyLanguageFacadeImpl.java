// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyLanguageFacadeBase;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ServiceImpl;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
@ServiceImpl
public class PyLanguageFacadeImpl extends PyLanguageFacadeBase {
    @Override
    protected LanguageLevel doGetEffectiveLanguageLevel(Project project, VirtualFile virtualFile) {
        return PythonLanguageLevelPusher.getEffectiveLanguageLevel(project, virtualFile);
    }

    @Override
    public void setEffectiveLanguageLevel(VirtualFile virtualFile, @Nullable LanguageLevel languageLevel) {
        PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, languageLevel);
    }
}
