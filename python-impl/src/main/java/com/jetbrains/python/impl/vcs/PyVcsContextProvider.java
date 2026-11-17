// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.vcs;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.ui.event.ComponentEvent;
import consulo.versionControlSystem.codeVision.VcsCodeVisionLanguageContext;

@ExtensionImpl
public final class PyVcsContextProvider implements VcsCodeVisionLanguageContext {
    @Override
    public boolean isAccepted(PsiElement element) {
        return element instanceof PyClass || element instanceof PyFunction;
    }

    @Override
    public void handleClick(ComponentEvent<?> mouseEvent,
                            Editor editor,
                            PsiElement element) {

    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
