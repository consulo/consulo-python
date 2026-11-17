/*
  * Copyright 2000-2015 JetBrains s.r.o.
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
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.GoogleCodeStyleDocStringBuilder;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
  * @author Mikhail Golubev
  */
public class GoogleDocStringSectionFixer extends PyFixer<PyStringLiteralExpression> {
    public GoogleDocStringSectionFixer() {
        super(PyStringLiteralExpression.class);
    }

    @Override
    protected boolean isApplicable(Editor editor, PyStringLiteralExpression pyString) {
        return DocStringUtil.getParentDefinitionDocString(pyString) == pyString &&
               DocStringParser.guessDocStringFormat(pyString.getText(), pyString) == DocStringFormat.GOOGLE;
    }

    @Override
    public void doApply(Editor editor, PySmartEnterProcessor processor, PyStringLiteralExpression pyString) {
        int offset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        int lineNum = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNum);
        int lineEnd = document.getLineEndOffset(lineNum);
        String line = document.getText(TextRange.create(lineStart, lineEnd));
        if (!StringUtil.isEmptyOrSpaces(line)) {
            String trimmedLine = line.trim();
            String header = trimmedLine.endsWith(":") ? trimmedLine.substring(0, trimmedLine.length() - 1) : trimmedLine;
            if (SectionBasedDocString.isValidSectionTitle(header)) {
                String patch = (trimmedLine.endsWith(":") ? "\n" : ":\n") +
                                     PyIndentUtil.getLineIndent(line) +
                                     GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(pyString.getContainingFile());
                int insertionOffset = lineStart + StringUtil.trimTrailing(line).length();
                document.replaceString(insertionOffset, lineEnd, patch);
                processor.registerUnresolvedError(insertionOffset + patch.length());
            }
        }
    }
}
