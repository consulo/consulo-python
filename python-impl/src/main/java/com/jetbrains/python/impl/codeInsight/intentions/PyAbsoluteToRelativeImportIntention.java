// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.codeInsight.imports.PyRelativeImportData;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Comparing;

/**
 * Converts location of 'from one.two.three import foo' to the one relative to the current file, e.g. 'from .three import foo'.
 *
 * @author Aleksei.Kniazev
 * @see PyRelativeToAbsoluteImportIntention
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyAbsoluteToRelativeImportIntention", categories = "Python", fileExtensions = "py")
public class PyAbsoluteToRelativeImportIntention extends PyConvertImportIntentionAction {
    public PyAbsoluteToRelativeImportIntention() {
        super(PyLocalize.intnConvertAbsoluteToRelative());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PyFile)) {
            return false;
        }
        PyFromImportStatement statement = findStatement(element);
        if (statement == null) {
            return false;
        }
        if (statement.getRelativeLevel() != 0) {
            return false;
        }

        QualifiedName targetPath = statement.getImportSourceQName();
        if (targetPath == null) {
            return false;
        }
        QualifiedName filePath = QualifiedNameFinder.findCanonicalImportPath(file, null);
        if (filePath == null) {
            return false;
        }
        return Comparing.equal(targetPath.getFirstComponent(), filePath.getFirstComponent());
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        PyFromImportStatement statement = findStatement(element);
        if (statement == null) {
            return;
        }
        QualifiedName targetPath = statement.getImportSourceQName();
        if (targetPath == null) {
            return;
        }
        PsiFile file = element.getContainingFile();
        PyRelativeImportData importData = PyRelativeImportData.fromString(targetPath.toString(), (PyFile) file);
        if (importData == null) {
            return;
        }

        replaceImportStatement(statement, file, importData.getLocationWithDots());
    }
}
