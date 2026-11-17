// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.Language;
import consulo.language.editor.action.GotoSuperActionHander;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.ex.popup.JBPopup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public final class PyGotoSuperHandler implements GotoSuperActionHander {

    @Override
    @RequiredReadAction
    public void invoke(Project project, Editor editor, PsiFile psiFile) {
        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        if (pyClass != null) {
            PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false, PyClass.class);
            if (function != null) {
                gotoSuperFunctions(editor, function, pyClass);
            }
            else {
                PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class, false, PyClass.class);
                if (assignment != null && assignment.getTargets()[0] instanceof PyTargetExpression) {
                    gotoSuperClassAttributes(editor, (PyTargetExpression) assignment.getTargets()[0], pyClass);
                }
                else {
                    TypeEvalContext context = TypeEvalContext.codeAnalysis(project, psiFile);
                    navigateOrChoose(editor, psiFile, pyClass.getAncestorClasses(context), PyCodeInsightLocalize.gotoSuperclassChoose());
                }
            }
        }
    }

    @RequiredReadAction
    private static void gotoSuperFunctions(Editor editor, PyFunction function, PyClass pyClass) {
        Collection<PyFunction> superFunctions = getAllSuperMethodsByName(function, pyClass);
        navigateOrChoose(editor, function, superFunctions, CodeInsightLocalize.gotoSuperMethodChooserTitle());
    }

    @RequiredReadAction
    private static void gotoSuperClassAttributes(Editor editor, PyTargetExpression attr, PyClass pyClass) {
        Collection<PyTargetExpression> attrs = getAllSuperAttributesByName(attr, pyClass);
        navigateOrChoose(editor, attr, attrs, PyCodeInsightLocalize.codeInsightGotoSuperclassAttributeChooserTitle());
    }

    @RequiredReadAction
    private static void navigateOrChoose(
        Editor editor,
        PsiElement source,
        Collection<? extends NavigatablePsiElement> superElements,
        LocalizeValue title
    ) {
        NavigatablePsiElement[] superElementArray = superElements.stream()
            .map(element -> PyiUtil.getNavigationTarget(element, source) instanceof NavigatablePsiElement target ? target : element)
            .distinct()
            .toArray(NavigatablePsiElement[]::new);
        if (superElementArray.length == 1) {
            superElementArray[0].navigate(true);
        }
        else if (superElementArray.length > 1) {
            JBPopup popup = PopupNavigationUtil.getPsiElementPopup(superElementArray, title.get());
            EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
        }
    }

    private static Collection<PyTargetExpression> getAllSuperAttributesByName(PyTargetExpression classAttr, PyClass pyClass) {
        String name = classAttr.getName();
        if (name == null) {
            return Collections.emptyList();
        }
        List<PyTargetExpression> result = new ArrayList<>();
        for (PyClass aClass : pyClass.getAncestorClasses(null)) {
            PyTargetExpression superAttr = aClass.findClassAttribute(name, false, null);
            if (superAttr != null) {
                result.add(superAttr);
            }
        }
        return result;
    }

    @RequiredReadAction
    private static Collection<PyFunction> getAllSuperMethodsByName(PyFunction method, PyClass pyClass) {
        String name = method.getName();
        if (name == null) {
            return Collections.emptyList();
        }
        List<PyFunction> result = new ArrayList<>();
        for (PyClass aClass : pyClass.getAncestorClasses(null)) {
            PyFunction byName = aClass.findMethodByName(name, false, null);
            if (byName != null) {
                result.add(byName);
            }
        }
        return result;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public boolean isValidFor(Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
