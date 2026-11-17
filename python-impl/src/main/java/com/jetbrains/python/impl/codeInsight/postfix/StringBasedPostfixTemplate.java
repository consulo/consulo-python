// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.postfixTemplate.PostfixTemplatesUtils;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelector;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateWithExpressionSelector;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TextExpression;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Port of the platform {@code com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate}.
 */
// TODO consulo: StringBasedPostfixTemplate missing in consulo.language.editor.refactoring.postfixTemplate - move there and drop this copy
public abstract class StringBasedPostfixTemplate extends PostfixTemplateWithExpressionSelector {

    public static final String EXPR = "expr";

    public StringBasedPostfixTemplate(String name,
                                      String example,
                                      PostfixTemplateExpressionSelector selector,
                                      @Nullable PostfixTemplateProvider provider) {
        super(null, name, example, selector, provider);
    }

    public StringBasedPostfixTemplate(String name,
                                      String key,
                                      String example,
                                      PostfixTemplateExpressionSelector selector,
                                      @Nullable PostfixTemplateProvider provider) {
        super(null, name, key, example, selector, provider);
    }

    @Override
    public void expandForChooseExpression(PsiElement expr, Editor editor) {
        Project project = expr.getProject();
        Document document = editor.getDocument();
        PsiElement elementForRemoving = getElementToRemove(expr);
        document.deleteString(elementForRemoving.getTextRange().getStartOffset(), elementForRemoving.getTextRange().getEndOffset());
        TemplateManager manager = TemplateManager.getInstance(project);

        String templateString = getTemplateString(expr);
        if (templateString == null) {
            PostfixTemplatesUtils.showErrorHint(expr.getProject(), editor);
            return;
        }


        Template template = createTemplate(manager, templateString);
        template.addVariable(EXPR, new TextExpression(expr.getText()), false);
        setVariables(template, expr);
        manager.startTemplate(editor, template);
    }

    public Template createTemplate(TemplateManager manager, String templateString) {
        Template template = manager.createTemplate("", "", templateString);
        template.setToReformat(shouldReformat());
        return template;
    }

    public void setVariables(Template template, PsiElement element) {
    }

    public abstract @Nullable String getTemplateString(PsiElement element);

    protected boolean shouldReformat() {
        return true;
    }

    protected PsiElement getElementToRemove(PsiElement expr) {
        return expr.getParent();
    }
}
