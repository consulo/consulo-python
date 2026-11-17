// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix {
    private final @Nullable SmartPsiElementPointer<PyNamedParameter> myMissingParam;
    private final @Nullable String myUnexpectedParamName;

    public DocstringQuickFix(@Nullable PyNamedParameter missing, @Nullable String unexpectedParamName) {
        if (missing != null) {
            myMissingParam = SmartPointerManager.getInstance(missing.getProject()).createSmartPsiElementPointer(missing);
        }
        else {
            myMissingParam = null;
        }
        myUnexpectedParamName = unexpectedParamName;
    }

    @Override
    public LocalizeValue getName() {
        if (myMissingParam != null) {
            PyNamedParameter param = myMissingParam.getElement();
            if (param == null) {
                throw new IncorrectOperationException("Parameter was invalidates before quickfix is called");
            }
            return PyLocalize.qfixDocstringAddParameter(param.getName());
        }
        else if (myUnexpectedParamName != null) {
            return PyLocalize.qfixDocstringRemoveParameter(myUnexpectedParamName);
        }
        else {
            return PyLocalize.qfixDocstringInsertStub();
        }
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
        if (docStringOwner == null) {
            return;
        }
        PyStringLiteralExpression docStringExpression = docStringOwner.getDocStringExpression();
        if (docStringExpression == null && myMissingParam == null && myUnexpectedParamName == null) {
            addEmptyDocstring(docStringOwner);
            return;
        }
        if (docStringExpression != null) {
            PyDocstringGenerator generator = PyDocstringGenerator.forDocStringOwner(docStringOwner);
            if (myMissingParam != null) {
                PyNamedParameter param = myMissingParam.getElement();
                if (param != null) {
                    generator.withParam(param);
                }
            }
            else if (myUnexpectedParamName != null) {
                generator.withoutParam(myUnexpectedParamName.trim());
            }
            generator.buildAndInsert();
        }
    }

    private static void addEmptyDocstring(PyDocStringOwner docStringOwner) {
        if (docStringOwner instanceof PyFunction ||
                docStringOwner instanceof PyClass && ((PyClass)docStringOwner).findInitOrNew(false, null) != null) {
            doGenerateDocstring(docStringOwner, PyQuickFixUtil.getEditor(docStringOwner));
        }
    }

    public static void doGenerateDocstring(PyDocStringOwner docStringOwner, @Nullable Editor editor) {
        PyDocstringGenerator docstringGenerator = PyDocstringGenerator
            .forDocStringOwner(docStringOwner)
            .withInferredParameters(false)
            .addFirstEmptyLine();
        PyAstStringLiteralExpression updated = docstringGenerator.buildAndInsert().getDocStringExpression();
        if (updated != null && editor != null) {
            int offset = updated.getTextOffset();
            editor.getCaretModel().moveToOffset(offset);
            editor.getCaretModel().moveCaretRelatively(0, 1, false, false, false);
        }
    }
}
