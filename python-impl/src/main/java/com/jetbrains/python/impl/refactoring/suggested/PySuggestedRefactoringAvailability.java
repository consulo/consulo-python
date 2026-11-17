// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.suggested;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.suggested.SuggestedChangeSignatureData;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringAvailability;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringData;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringState;
import consulo.language.editor.refactoring.suggested.SuggestedRenameData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import org.jspecify.annotations.Nullable;

class PySuggestedRefactoringAvailability extends SuggestedRefactoringAvailability {
    PySuggestedRefactoringAvailability(PySuggestedRefactoringSupport support) {
        super(support);
    }

    @Override
    @RequiredReadAction
    public boolean shouldSuppressRefactoringForDeclaration(SuggestedRefactoringState state) {
        return PySuggestedRefactoringSupport.Helper.shouldSuppressRefactoringForDeclaration(state);
    }

    @Override
    @RequiredReadAction
    public @Nullable SuggestedRefactoringData detectAvailableRefactoring(SuggestedRefactoringState state) {
        PsiElement declaration = state.getDeclaration();
        if (declaration == null) {
            return null;
        }
        if (PySuggestedRefactoringSupport.Helper.isAvailableForChangeSignature(declaration)) {
            return SuggestedChangeSignatureData.create(state, RefactoringLocalize.suggestedRefactoringUsages());
        }
        if (PySuggestedRefactoringSupport.Helper.isAvailableForRename(declaration)) {
            return new SuggestedRenameData((PsiNameIdentifierOwner) declaration, state.getOldSignature().getName());
        }
        return null;
    }
}
