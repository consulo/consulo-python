// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

/**
 * User: catherine
 * <p>
 * QuickFix to convert docstrings to the common form according to PEP-257
 * For consistency, always use """triple double quotes""" around docstrings.
 */
public class ConvertDocstringQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixConvertSingleQuotedDocstring();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyStringLiteralExpression && element.isWritable()) {
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

            String stringText = element.getText();
            int prefixLength = PyStringLiteralUtil.getPrefixLength(stringText);
            String prefix = stringText.substring(0, prefixLength);
            String content = stringText.substring(prefixLength);
            if (content.startsWith("'''")) {
                content = content.substring(3, content.length() - 3);
            }
            else if (content.startsWith("\"\"\"")) {
                return;
            }
            else {
                content = content.length() == 1 ? "" : content.substring(1, content.length() - 1);
            }
            if (content.endsWith("\"")) {
                // TODO consulo: StringUtil.replaceSubstring(String, TextRange, String) missing
                content = StringUtil.replaceSubstring(content, content.length() - 1, content.length(), "\\\"");
            }

            PyExpression newString = elementGenerator.createDocstring(prefix + "\"\"\"" + content + "\"\"\"").getExpression();
            element.replace(newString);
        }
    }
}
