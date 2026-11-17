// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.util.HtmlBuilder;
import consulo.language.Language;
import consulo.language.editor.ExpressionTypeProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntaxTraverser;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.util.lang.StringUtil;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class PythonExpressionTypeProvider extends ExpressionTypeProvider<PyExpression> {
    @Override
    public String getInformationHint(PyExpression element) {
        TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
        // The type has no links yet, see PY-93419
        return withHtmlLineBreaks(PythonDocumentationProvider.getMultilineHighlightedTypeName(context.getType(element), context, element));
    }

    private static String withHtmlLineBreaks(String html) {
        HtmlBuilder result = new HtmlBuilder();
        for (String line : StringUtil.splitByLines(html, false)) {
            if (!result.isEmpty()) {
                result.br();
            }
            String content = StringUtil.trimLeading(line, ' ');
            int indent = line.length() - content.length();
            if (indent > 0) {
                result.nbsp(indent);
            }
            result.appendRaw(content);
        }
        return result.toString();
    }

    @Override
    public String getErrorHint() {
        return PyCodeInsightLocalize.showExpressionTypeNoExpressionFound().get();
    }

    @Override
    public List<PyExpression> getExpressionsAt(PsiElement elementAt) {
        return SyntaxTraverser.psiApi()
            .parents(elementAt)
            .takeWhile(e -> !(e instanceof PsiFile))
            .filter(PyExpression.class)
            .toList();
    }

    @Override
    public boolean hasAdvancedInformation() {
        return Application.get().isInternal();
    }

    @Override
    public String getAdvancedInformationHint(PyExpression element) {
        TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
        TypeEvalContext userInitiated = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
        return """
            TypeEvalContext.userInitiated: %s
            TypeEvalContext.codeAnalysis: %s
            """.formatted(
            PythonDocumentationProvider.getTypeName(userInitiated.getType(element), userInitiated),
            PythonDocumentationProvider.getTypeName(codeAnalysis.getType(element), codeAnalysis)
        );
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
