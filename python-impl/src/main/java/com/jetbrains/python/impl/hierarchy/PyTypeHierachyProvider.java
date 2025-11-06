/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.hierarchy;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.ide.hierarchy.TypeHierarchyBrowserBase;
import consulo.language.Language;
import consulo.language.editor.hierarchy.HierarchyBrowser;
import consulo.language.editor.hierarchy.TypeHierarchyProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 * @since 2009-07-31
 */
@ExtensionImpl
public class PyTypeHierachyProvider implements TypeHierarchyProvider {
    @Nullable
    @Override
    @RequiredReadAction
    public PsiElement getTarget(@Nonnull DataContext dataContext) {
        PsiElement element = dataContext.getData(PsiElement.KEY);
        if (element == null) {
            Editor editor = dataContext.getData(Editor.KEY);
            PsiFile file = dataContext.getData(PsiFile.KEY);
            if (editor != null && file != null) {
                element = file.findElementAt(editor.getCaretModel().getOffset());
            }
        }
        if (!(element instanceof PyClass)) {
            element = PsiTreeUtil.getParentOfType(element, PyClass.class);
        }
        return element;
    }

    @Nonnull
    @Override
    public HierarchyBrowser createHierarchyBrowser(PsiElement target) {
        return new PyTypeHierarchyBrowser((PyClass) target);
    }

    @Override
    @RequiredReadAction
    public void browserActivated(@Nonnull HierarchyBrowser hierarchyBrowser) {
        ((PyTypeHierarchyBrowser) hierarchyBrowser).changeView(TypeHierarchyBrowserBase.TYPE_HIERARCHY_TYPE);
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
