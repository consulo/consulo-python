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
import consulo.language.Language;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyProvider;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 * @since 2009-07-31
 */
@ExtensionImpl
public class PyTypeHierarchyProvider implements HierarchyProvider<PyClass> {
    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.TYPE;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyClass getTarget(DataContext dataContext) {
        PsiElement element = dataContext.getData(PsiElement.KEY);
        if (element == null) {
            Editor editor = dataContext.getData(Editor.KEY);
            PsiFile file = dataContext.getData(PsiFile.KEY);
            if (editor != null && file != null) {
                element = file.findElementAt(editor.getCaretModel().getOffset());
            }
        }
        if (element instanceof PyClass pyClass) {
            return pyClass;
        }
        return PsiTreeUtil.getParentOfType(element, PyClass.class);
    }

    @RequiredReadAction
    @Override
    public HierarchyModel<PyClass> createModel(Project project, PyClass target) {
        return new PyTypeHierarchyModel(target);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
