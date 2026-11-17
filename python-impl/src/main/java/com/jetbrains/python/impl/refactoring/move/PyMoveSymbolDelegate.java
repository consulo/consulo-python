// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel.PyMakeFunctionTopLevelDialog;
import com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel.PyMakeLocalFunctionTopLevelProcessor;
import com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel.PyMakeMethodTopLevelProcessor;
import com.jetbrains.python.impl.refactoring.move.moduleMembers.PyMoveModuleMembersDialog;
import com.jetbrains.python.impl.refactoring.move.moduleMembers.PyMoveModuleMembersHelper;
import com.jetbrains.python.impl.refactoring.move.moduleMembers.PyMoveModuleMembersProcessor;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

@ExtensionImpl
public final class PyMoveSymbolDelegate extends MoveHandlerDelegate {
    @Override
    @RequiredReadAction
    public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
        if (targetContainer != null && !super.canMove(elements, targetContainer)) {
            return false;
        }
        // Local function or method
        if (isMovableLocalFunctionOrMethod(elements[0])) {
            return true;
        }

        // Top-level module member
        for (PsiElement element : elements) {
            if (!PyMoveModuleMembersHelper.isMovableModuleMember(element)) {
                return false;
            }
        }
        return true;
    }

    @RequiredReadAction
    public void doMove(Project project, List<PyElement> elements) {
        PsiElement firstElement = elements.get(0);
        String initialPath = StringUtil.notNullize(PyPsiUtils.getContainingFilePath(firstElement));

        BaseRefactoringProcessor processor;
        if (isMovableLocalFunctionOrMethod(firstElement)) {
            PyFunction function = (PyFunction) firstElement;
            PyMakeFunctionTopLevelDialog dialog = new PyMakeFunctionTopLevelDialog(project, function, initialPath, initialPath);
            if (!dialog.showAndGet()) {
                return;
            }
            if (function.getContainingClass() != null) {
                processor = new PyMakeMethodTopLevelProcessor(function, dialog.getTargetPath());
            }
            else {
                processor = new PyMakeLocalFunctionTopLevelProcessor(function, dialog.getTargetPath());
            }
            processor.setPreviewUsages(dialog.isPreviewUsages());
        }
        else {
            List<PsiNamedElement> initialElements = new ArrayList<>();
            for (PsiElement element : elements) {
                PsiNamedElement e = PyMoveModuleMembersHelper.extractNamedElement(element);
                if (e == null) {
                    return;
                }
                initialElements.add(e);
            }
            PyMoveModuleMembersDialog dialog = new PyMoveModuleMembersDialog(project, initialElements, initialPath, initialPath);
            if (!dialog.showAndGet()) {
                return;
            }
            PsiNamedElement[] selectedElements = dialog.getSelectedTopLevelSymbols()
                .stream()
                .filter(PsiNamedElement.class::isInstance)
                .map(PsiNamedElement.class::cast)
                .toArray(PsiNamedElement[]::new);
            processor = new PyMoveModuleMembersProcessor(selectedElements, dialog.getTargetPath());
            processor.setPreviewUsages(dialog.isPreviewUsages());
        }

        try {
            processor.run();
        }
        catch (IncorrectOperationException e) {
            if (Application.get().isUnitTestMode()) {
                throw e;
            }
            CommonRefactoringUtil.showErrorMessage(
                RefactoringLocalize.errorTitle(),
                LocalizeValue.ofNullable(e.getMessage()),
                null,
                project
            );
        }
    }

    @Override
    @RequiredReadAction
    public boolean tryToMove(
        PsiElement element,
        Project project,
        @Nullable DataContext dataContext,
        @Nullable PsiReference reference,
        @Nullable Editor editor
    ) {
        PsiFile currentFile = element.getContainingFile();
        if (editor != null && currentFile instanceof PyFile pyFile && selectionSpansMultipleLines(editor)) {
            List<PyElement> moduleMembers = collectAllMovableElementsInSelection(editor, pyFile);
            if (moduleMembers.isEmpty()) {
                showBadSelectionErrorHint(project, editor);
            }
            else {
                doMove(project, moduleMembers);
            }
            return true;
        }

        // Fallback to the old way to select single element to move
        PsiNamedElement e = PyMoveModuleMembersHelper.extractNamedElement(element);
        if (e != null && PyMoveModuleMembersHelper.hasMovableElementType(e)) {
            if (PyMoveModuleMembersHelper.isMovableModuleMember(e) || isMovableLocalFunctionOrMethod(e)) {
                doMove(project, Collections.singletonList((PyElement) e));
            }
            else {
                showBadSelectionErrorHint(project, editor);
            }
            return true;
        }
        return false;
    }

    private static void showBadSelectionErrorHint(Project project, @Nullable Editor editor) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            PyLocalize.refactoringMoveModuleMembersErrorSelection(),
            RefactoringLocalize.errorTitle(),
            null
        );
    }

    private static boolean selectionSpansMultipleLines(Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        Document document = editor.getDocument();
        return document.getLineNumber(selectionModel.getSelectionStart()) != document.getLineNumber(selectionModel.getSelectionEnd());
    }

    private static List<PyElement> collectAllMovableElementsInSelection(Editor editor, PyFile pyFile) {
        SelectionModel selectionModel = editor.getSelectionModel();
        TextRange selectionRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        List<PyElement> members = PyMoveModuleMembersHelper.getTopLevelModuleMembers(pyFile);
        return ContainerUtil.filter(members, member -> {
            PsiElement body = PyMoveModuleMembersHelper.expandNamedElementBody((PsiNamedElement) member);
            return body != null && selectionRange.contains(body.getTextRange());
        });
    }

    public static boolean isMovableLocalFunctionOrMethod(PsiElement element) {
        return isLocalFunction(element) || isSuitableInstanceMethod(element);
    }

    private static boolean isSuitableInstanceMethod(@Nullable PsiElement element) {
        PyFunction function = as(element, PyFunction.class);
        if (function == null || function.getContainingClass() == null) {
            return false;
        }
        String funcName = function.getName();
        if (funcName == null || PyUtilCore.isSpecialName(funcName)) {
            return false;
        }
        TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(function.getProject(), function.getContainingFile());
        if (PySuperMethodsSearch.search(function, typeEvalContext).findFirst() != null) {
            return false;
        }
        if (PyOverridingMethodsSearch.search(function, true).findFirst() != null) {
            return false;
        }
        if (function.getDecoratorList() != null || function.getModifier() != null) {
            return false;
        }
        if (function.getContainingClass().findPropertyByCallable(function) != null) {
            return false;
        }
        return true;
    }

    private static boolean isLocalFunction(@Nullable PsiElement resolved) {
        return resolved instanceof PyFunction && PsiTreeUtil.getParentOfType(resolved, ScopeOwner.class, true) instanceof PyFunction;
    }

    // TODO consulo: MoveHandlerDelegate#supportsLanguage missing
    public boolean supportsLanguage(Language language) {
        return language.isKindOf(PythonLanguage.getInstance());
    }
}
