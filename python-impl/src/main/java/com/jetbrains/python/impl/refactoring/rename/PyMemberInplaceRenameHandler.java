// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.rename;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.editor.refactoring.rename.inplace.MemberInplaceRenameHandler;
import consulo.language.editor.refactoring.rename.inplace.MemberInplaceRenamer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PyMemberInplaceRenameHandler extends MemberInplaceRenameHandler {
    @Override
    @RequiredReadAction
    protected boolean isAvailable(@Nullable PsiElement element, Editor editor, PsiFile file) {
        PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
        if (nameSuggestionContext == null && editor.getCaretModel().getOffset() > 0) {
            nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset() - 1);
        }

        PsiElement target = element;
        if (target == null && LookupManager.getActiveLookup(editor) != null) {
            target = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement.class);
        }
        if (target == null) {
            return false;
        }

        return editor.getSettings().isVariableInplaceRenameEnabled() &&
            target instanceof PsiNameIdentifierOwner &&
            target instanceof PyElement;
    }

    @Override
    protected MemberInplaceRenamer createMemberRenamer(PsiElement element, PsiNameIdentifierOwner elementToRename, Editor editor) {
        if (element instanceof PyClass elementClass && elementToRename instanceof PyFunction function &&
            function.getContainingClass() == element && PyUtil.isInitOrNewMethod(elementToRename)) {
            return new MemberInplaceRenamer(elementClass, element, editor);
        }
        return new MemberInplaceRenamer(elementToRename, element, editor);
    }
}
