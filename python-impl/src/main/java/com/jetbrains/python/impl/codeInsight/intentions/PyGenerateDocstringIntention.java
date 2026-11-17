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

import com.jetbrains.python.impl.inspections.quickfix.DocstringQuickFix;
import java.util.List;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.util.collection.ArrayUtil;
import consulo.ui.ex.awt.Messages;
import consulo.module.Module;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * Intention to add documentation string for function (with checked format).
 *
 * @author catherine
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyGenerateDocstringIntention", categories = "Python", fileExtensions = "py")
public class PyGenerateDocstringIntention extends PyBaseIntentionAction {
    private LocalizeValue myText = LocalizeValue.empty();

    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile) || file instanceof PyDocstringFile) {
            return false;
        }
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        if (elementAt == null) {
            return false;
        }
        PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
        PyStatementList statementList = PsiTreeUtil.getParentOfType(elementAt, PyStatementList.class, false, PyFunction.class);
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

    @Override
    @RequiredReadAction
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

        if (function == null) {
            return;
        }

        generateDocstring(function, editor);
    }

    public static void generateDocstring(PyDocStringOwner docStringOwner, @Nullable Editor editor) {
        if (!ensureNotPlainDocstringFormat(docStringOwner)) {
            return;
        }
        DocstringQuickFix.doGenerateDocstring(docStringOwner, editor);
    }

    /**
     * Checks that docstring format is set either via element module's {@link com.jetbrains.python.PyNames#DOCFORMAT} attribute or
     * in module settings. If none of them applies, show standard choose dialog, asking user to pick one and updates module settings
     * accordingly.
     *
     * @param anchor PSI element that will be used to locate containing file and project module
     * @return false if no structured docstring format was specified initially and user didn't select any, true otherwise
     */
    public static boolean ensureNotPlainDocstringFormat(PsiElement anchor) {
        Module module = DocStringParser.getModuleForElement(anchor);
        if (module == null) {
            return false;
        }

        return ensureNotPlainDocstringFormatForFile(anchor.getContainingFile(), module);
    }

    private static boolean ensureNotPlainDocstringFormatForFile(PsiFile file, Module module) {
        PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
        if (settings.isPlain(file)) {
            List<String> values = DocStringFormat.ALL_NAMES_BUT_PLAIN;
            // TODO consulo: PythonUiService missing - JB: PythonUiService.getInstance().showChooseDialog(null, null, PyPsiBundle.message("python.docstring.format"), PyPsiBundle.message("python.docstring.select.type"), ...)
            int i =
                Messages.showChooseDialog("Docstring format:", "Select Docstring Type", ArrayUtil.toStringArray(values), values.get(0), null);
            if (i < 0) {
                return false;
            }
            settings.setFormat(DocStringFormat.fromNameOrPlain(values.get(i)));
        }
        return true;
    }
}
