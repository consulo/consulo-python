// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TextExpression;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

public class PyCastPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    public PyCastPostfixTemplate(PostfixTemplateProvider provider) {
        super("cast", "cast(, expr)", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider);
    }

    @Override
    public String getTemplateString(PsiElement element) {
        return "cast($END$, $expr$)";
    }

    @Override
    protected PsiElement getElementToRemove(PsiElement expr) {
        return expr;
    }

    @Override
    protected boolean shouldReformat() {
        return false;
    }

    @Override
    public void expandForChooseExpression(PsiElement expr, Editor editor) {
        Project project = expr.getProject();
        PsiFile file = expr.getContainingFile();
        Document document = editor.getDocument();
        String exprText = expr.getText();
        RangeMarker marker = document.createRangeMarker(getElementToRemove(expr).getTextRange());

        // Add the import while the expression is still present, then perform the textual expansion below it so that the
        // running live template is never disturbed by a document change happening above its segments.
        AddImportHelper.addOrUpdateFromImportStatement(file, "typing", "cast", null, AddImportHelper.ImportPriority.BUILTIN, null);
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);

        document.deleteString(marker.getStartOffset(), marker.getEndOffset());
        editor.getCaretModel().moveToOffset(marker.getStartOffset());
        marker.dispose();

        TemplateManager manager = TemplateManager.getInstance(project);
        Template template = createTemplate(manager, getTemplateString(expr));
        template.addVariable(StringBasedPostfixTemplate.EXPR, new TextExpression(exprText), false);
        manager.startTemplate(editor, template);
    }
}
