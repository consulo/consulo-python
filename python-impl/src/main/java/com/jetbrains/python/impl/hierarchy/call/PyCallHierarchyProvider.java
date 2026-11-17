// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.call;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyProvider;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * @author novokrest
 */
@ExtensionImpl
public final class PyCallHierarchyProvider implements HierarchyProvider<PsiElement> {
    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.CALL;
    }

    @Override
    @RequiredReadAction
    public @Nullable PsiElement getTarget(DataContext dataContext) {
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return null;
        }

        PsiElement element = dataContext.getData(PsiElement.KEY);
        if (element == null) {
            Editor editor = dataContext.getData(Editor.KEY);
            if (editor != null) {
                PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                if (file == null) {
                    return null;
                }

                element = TargetElementUtil.findTargetElement(editor, Set.of(
                    TargetElementUtilExtender.ELEMENT_NAME_ACCEPTED,
                    TargetElementUtilExtender.REFERENCED_ELEMENT_ACCEPTED,
                    TargetElementUtilExtender.LOOKUP_ITEM_ACCEPTED
                ));
                if (element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile) {
                    return element;
                }

                element = file.findElementAt(editor.getCaretModel().getOffset());
            }
        }
        return PsiTreeUtil.getNonStrictParentOfType(element, PyFunction.class, PyClass.class, PyFile.class);
    }

    @Override
    @RequiredReadAction
    public HierarchyModel<PsiElement> createModel(Project project, PsiElement target) {
        // Consulo: PyCallHierarchyModel replaces JB PyCallHierarchyBrowser (the platform owns the browser UI)
        return new PyCallHierarchyModel(project, target);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
