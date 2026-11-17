// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.structureView;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.fileEditor.structureView.StructureViewBuilder;
import consulo.fileEditor.structureView.StructureViewModel;
import consulo.fileEditor.structureView.TreeBasedStructureViewBuilder;
import consulo.language.Language;
import consulo.language.editor.structureView.PsiStructureViewFactory;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {

            @Override
            public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new PyStructureViewModel(psiFile, editor);
            }

            @Override
            public boolean isRootNodeShown() {
                return false;
            }
        };
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
