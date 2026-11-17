// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.postfixTemplate.PostfixTemplate;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiFile;

import java.util.Set;

@ExtensionImpl
public final class PyPostfixTemplateProvider extends PostfixTemplateProvider {

    @Override
    protected Set<PostfixTemplate> buildTemplates() {
        return Set.of(
            new PyNotPostfixTemplate(this),
            new PyParenthesizedExpressionPostfixTemplate(this),
            new PyReturnPostfixTemplate(this),
            new PyIfPostfixTemplate(this),
            new PyWhilePostfixTemplate(this),
            new PyForPostfixTemplate("for", this),
            new PyForPostfixTemplate("iter", this),
            new PyForEnumeratePostfixTemplate("fore", this),
            new PyForEnumeratePostfixTemplate("itere", this),
            new PyForEnumeratePostfixTemplate("enum", this),
            new PyIsNonePostfixTemplate(this),
            new PyIsNotNonePostfixTemplate(this),
            new PyPrintPostfixTemplate(this),
            new PyMainPostfixTemplate(this),
            new PyLenPostfixTemplate(this),
            new PyIsInstancePostfixTemplate(this),
            new PyCastPostfixTemplate(this),
            new PyAwaitPostfixTemplate(this),
            new PyWithPostfixTemplate(this),
            new PyStatementKeywordPostfixTemplate("raise", this),
            new PyStatementKeywordPostfixTemplate("yield", this),
            new PyCallWrapPostfixTemplate("str", this),
            new PyCallWrapPostfixTemplate("list", this),
            new PyCallWrapPostfixTemplate("set", this),
            new PyCallWrapPostfixTemplate("dict", this),
            new PyCallWrapPostfixTemplate("tuple", this),
            new PyComprehensionPostfixTemplate("compl", "[$VAR_EXPR$ for $VAR$ in $EXPR$]$END$", "[e for e in expr]", this),
            new PyComprehensionPostfixTemplate("comps", "{$VAR_EXPR$ for $VAR$ in $EXPR$}$END$", "{e for e in expr}", this),
            new PyComprehensionPostfixTemplate("compg", "($VAR_EXPR$ for $VAR$ in $EXPR$)$END$", "(e for e in expr)", this),
            new PyDictComprehensionPostfixTemplate(this)
        );
    }

    // TODO consulo: PostfixTemplateProvider.getId() missing - JB returns "builtin.python"
    // TODO consulo: PostfixTemplateProvider.getPresentableName() missing - JB returns PyCodeInsightBundle "postfix.template.provider.name"
    // TODO consulo: PostfixTemplateProvider.createEditor(PostfixTemplate) / PostfixTemplateEditor missing - JB creates PyPostfixTemplateEditor
    //   for PyEditablePostfixTemplate (or a new template)
    // TODO consulo: PostfixTemplateProvider.readExternalTemplate / writeExternalTemplate and
    //   PostfixTemplatesUtils.readExternalLiveTemplate / readExternalConditions / readExternalTopmostAttribute / writeExternalTemplate missing -
    //   JB reads user templates as PyEditablePostfixTemplate with conditions resolved by id via
    //   PyPostfixTemplateExpressionCondition.PyClassCondition.readFrom or PyPostfixTemplateExpressionCondition.PUBLIC_CONDITIONS

    @Override
    public boolean isTerminalSymbol(char currentChar) {
        return currentChar == '.' || currentChar == '!';
    }

    @Override
    public void preExpand(PsiFile file, Editor editor) {

    }

    @Override
    public void afterExpand(PsiFile file, Editor editor) {

    }

    @Override
    public PsiFile preCheck(PsiFile copyFile, Editor realEditor, int currentOffset) {
        return copyFile;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.getInstance();
    }
}
