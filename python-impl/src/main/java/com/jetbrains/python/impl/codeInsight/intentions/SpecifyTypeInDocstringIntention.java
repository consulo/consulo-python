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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Helps to specify type.
 *
 * @author ktisha
 */
public class SpecifyTypeInDocstringIntention extends TypeIntention {
    @Nonnull
    private LocalizeValue myText = PyLocalize.intnSpecifyType();

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Override
    public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyExpression problemElement = getProblemElement(elementAt);
        PsiReference reference = problemElement == null ? null : problemElement.getReference();

        PsiElement resolved = reference != null ? reference.resolve() : null;
        PyNamedParameter parameter = getParameter(problemElement, resolved);

        PyCallable callable;
        if (parameter != null) {
            callable = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        }
        else {
            callable = getCallable(elementAt);
        }
        if (callable instanceof PyFunction) {
            generateDocstring(parameter, (PyFunction) callable);
        }
    }

    private static void generateDocstring(@Nullable PyNamedParameter param, @Nonnull PyFunction pyFunction) {
        if (!DocStringUtil.ensureNotPlainDocstringFormat(pyFunction)) {
            return;
        }

        PyDocstringGenerator docstringGenerator = PyDocstringGenerator.forDocStringOwner(pyFunction);
        String type = PyNames.OBJECT;
        if (param != null) {
            String paramName = StringUtil.notNullize(param.getName());
            PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
            if (signature != null) {
                type = ObjectUtil.chooseNotNull(signature.getArgTypeQualifiedName(paramName), type);
            }
            docstringGenerator.withParamTypedByName(param, type);
        }
        else {
            PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
            if (signature != null) {
                type = ObjectUtil.chooseNotNull(signature.getReturnTypeQualifiedName(), type);
            }
            docstringGenerator.withReturnValue(type);
        }

        docstringGenerator.addFirstEmptyLine().buildAndInsert();
        docstringGenerator.startTemplate();
    }

    @Override
    protected void updateText(boolean isReturn) {
        myText = isReturn ? PyLocalize.intnSpecifyReturnType() : PyLocalize.intnSpecifyType();
    }

    @Override
    protected boolean isParamTypeDefined(@Nonnull PyParameter parameter) {
        PyFunction pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        if (pyFunction != null) {
            StructuredDocString structuredDocString = pyFunction.getStructuredDocString();
            if (structuredDocString == null) {
                return false;
            }
            Substring typeSub = structuredDocString.getParamTypeSubstring(StringUtil.notNullize(parameter.getName()));
            return typeSub != null && !typeSub.isEmpty();
        }
        return false;
    }

    @Override
    protected boolean isReturnTypeDefined(@Nonnull PyFunction function) {
        StructuredDocString structuredDocString = function.getStructuredDocString();
        return structuredDocString != null && structuredDocString.getReturnType() != null;
    }
}
