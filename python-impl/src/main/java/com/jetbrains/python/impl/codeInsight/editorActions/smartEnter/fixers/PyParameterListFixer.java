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
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class PyParameterListFixer extends PyFixer<PyParameterList> {
    public PyParameterListFixer() {
        super(PyParameterList.class);
    }

    @Override
    public void doApply(Editor editor, PySmartEnterProcessor processor, PyParameterList parameters)
        throws IncorrectOperationException {
        PsiElement lBrace = PyPsiUtils.getChildByFilter(parameters, PyTokenTypes.OPEN_BRACES, 0);
        PsiElement rBrace = PyPsiUtils.getChildByFilter(parameters, PyTokenTypes.CLOSE_BRACES, 0);
        PyFunction pyFunction = as(parameters.getParent(), PyFunction.class);
        if (pyFunction != null && !PyFunctionFixer.isFakeFunction(pyFunction) && (lBrace == null || rBrace == null)) {
            Document document = editor.getDocument();
            if (lBrace == null) {
                String textToInsert = pyFunction.getNameNode() == null ? " (" : "(";
                document.insertString(parameters.getTextOffset(), textToInsert);
                insertParametersForMethod(pyFunction, document, parameters.getTextOffset() + textToInsert.length());
            }
            else if (parameters.getParameters().length == 0) {
                int lBraceOffset = lBrace.getTextRange().getEndOffset();
                int offsetWithParam = insertParametersForMethod(pyFunction, document, lBraceOffset);
                document.insertString(offsetWithParam, ")");
            }
            else {
                document.insertString(parameters.getTextRange().getEndOffset(), ")");
            }
        }
    }

    private static int insertParametersForMethod(PyFunction pyFunction, Document document, int offset) {
        if (pyFunction.getContainingClass() != null) {
            PyFunction.Modifier modifier = pyFunction.getModifier();
            String parameterName = null;
            if (modifier == null) {
                parameterName = PyNames.CANONICAL_SELF;
            }
            else if (modifier == PyAstFunction.Modifier.CLASSMETHOD) {
                parameterName = PyNames.CANONICAL_CLS;
            }

            if (parameterName != null) {
                document.insertString(offset, parameterName);
                return offset + parameterName.length();
            }
        }
        return offset;
    }
}
