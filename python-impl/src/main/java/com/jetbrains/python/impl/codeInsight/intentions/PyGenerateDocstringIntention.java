/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Intention to add documentation string for function (with checked format).
 *
 * @author catherine
 */
public class PyGenerateDocstringIntention extends PyBaseIntentionAction {
    @Nonnull
    private LocalizeValue myText = LocalizeValue.empty();

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myText;
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile) || file instanceof PyDocstringFile) {
            return false;
        }
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        if (elementAt == null) {
            return false;
        }
        PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
        final PyStatementList statementList = PsiTreeUtil.getParentOfType(elementAt, PyStatementList.class, false, PyFunction.class);
        if (function == null || statementList != null) {
            return false;
        }
        if (!elementAt.equals(function.getNameNode())) {
            return false;
        }
        return isAvailableForFunction(function);
    }

    private boolean isAvailableForFunction(PyFunction function) {
        if (function.getDocStringValue() != null) {
            if (PyDocstringGenerator.forDocStringOwner(function).withInferredParameters(false).hasParametersToAdd()) {
                myText = PyLocalize.intnAddParametersToDocstring();
                return true;
            }
            else {
                return false;
            }
        }
        else {
            myText = PyLocalize.intnDocStringStub();
            return true;
        }
    }

    public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

        if (function == null) {
            return;
        }

        generateDocstring(function, editor);
    }

    public static void generateDocstring(@Nonnull PyDocStringOwner docStringOwner, @Nullable Editor editor) {
        if (!DocStringUtil.ensureNotPlainDocstringFormat(docStringOwner)) {
            return;
        }
        final PyDocstringGenerator docstringGenerator =
            PyDocstringGenerator.forDocStringOwner(docStringOwner).withInferredParameters(false).addFirstEmptyLine();
        final PyStringLiteralExpression updated = docstringGenerator.buildAndInsert().getDocStringExpression();
        if (updated != null && editor != null) {
            final int offset = updated.getTextOffset();
            editor.getCaretModel().moveToOffset(offset);
            editor.getCaretModel().moveCaretRelatively(0, 1, false, false, false);
        }
    }
}
