// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.editor.template.context.TemplateActionContext;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import org.jspecify.annotations.Nullable;

import static consulo.language.pattern.PlatformPatterns.psiElement;

public abstract class PythonTemplateContextType extends BaseTemplateContextType {

    public PythonTemplateContextType(String contextId, LocalizeValue presentableName) {
        super(contextId, presentableName);
    }

    public PythonTemplateContextType(
        String contextId,
        LocalizeValue presentableName,
        java.lang.Class<? extends TemplateContextType> baseContextType
    ) {
        super(contextId, presentableName, baseContextType);
    }

    @Override
    public boolean isInContext(TemplateActionContext templateActionContext) {
        PsiFile file = templateActionContext.getFile();
        if (isPythonLanguage(file, templateActionContext.getStartOffset())) {
            PsiElement element = file.findElementAt(templateActionContext.getStartOffset());
            if (element != null) {
                if (!templateActionContext.isSurrounding()) {
                    if (isAfterDot(element) || element instanceof PsiComment || isInsideStringLiteral(element) || isInsideParameterList(element)) {
                        return false;
                    }
                }
                return isInContext(element);
            }
        }
        return false;
    }

    @Override
    public @Nullable SyntaxHighlighter createHighlighter() {
        return new PyHighlighter(LanguageLevel.getLatest());
    }

    protected abstract boolean isInContext(PsiElement element);

    private static boolean isPythonLanguage(PsiFile file, int offset) {
        return PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(PythonLanguage.getInstance());
    }

    private static boolean isInsideStringLiteral(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class, false) != null;
    }

    private static boolean isInsideParameterList(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PyParameterList.class) != null;
    }

    private static boolean isAfterDot(PsiElement element) {
        PsiElementPattern.Capture<PsiElement> capture = psiElement().afterLeafSkipping(
            psiElement().whitespace(),
            psiElement().withElementType(PyTokenTypes.DOT)
        );
        return capture.accepts(element, new ProcessingContext());
    }

    @ExtensionImpl
    public static final class General extends PythonTemplateContextType {

        public General() {
            super("Python", LocalizeValue.of("Python")); //NON-NLS
        }

        @Override
        protected boolean isInContext(PsiElement element) {
            return true;
        }
    }

    @ExtensionImpl
    public static final class Class extends PythonTemplateContextType {
        public Class() {
            super("Python_Class", PyCodeInsightLocalize.liveTemplateContextClass(), General.class);
        }

        @Override
        protected boolean isInContext(PsiElement element) {
            return PsiTreeUtil.getParentOfType(element, PyClass.class) != null;
        }
    }

    @ExtensionImpl
    public static final class TopLevel extends PythonTemplateContextType {
        public TopLevel() {
            super("Python_Top_Level", PyCodeInsightLocalize.liveTemplateContextTopLevel(), General.class);
        }

        @Override
        protected boolean isInContext(PsiElement element) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(element);
            if (!(owner instanceof PyFile)) {
                return false;
            }
            PyExpressionStatement statement = PsiTreeUtil.getParentOfType(element, PyExpressionStatement.class);
            return statement != null && statement.getParent() instanceof PyFile;
        }
    }
}
