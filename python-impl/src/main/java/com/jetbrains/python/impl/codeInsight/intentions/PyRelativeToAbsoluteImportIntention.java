// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.extensions.PsiFileSystemItemExtKt;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * Converts location of 'from .three import foo' to absolute path, e.g. 'from one.two.three import foo'.
 *
 * @author Aleksei.Kniazev
 * @see PyRelativeToAbsoluteImportIntention
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyRelativeToAbsoluteImportIntention", categories = "Python", fileExtensions = "py")
public class PyRelativeToAbsoluteImportIntention extends PyConvertImportIntentionAction {
    public PyRelativeToAbsoluteImportIntention() {
        super(PyLocalize.intnConvertRelativeToAbsolute());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return false;
        }
        PyFromImportStatement statement = findStatement(element);
        if (statement == null) {
            return false;
        }
        return statement.getRelativeLevel() > 0;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        PyFromImportStatement statement = findStatement(element);
        if (statement == null) {
            return;
        }
        PsiFileSystemItem source = statement.resolveImportSource();
        if (source == null) {
            return;
        }
        QualifiedName qName = PsiFileSystemItemExtKt.getQName(source);

        replaceImportStatement(statement, element.getContainingFile(), String.valueOf(qName));
    }
}
