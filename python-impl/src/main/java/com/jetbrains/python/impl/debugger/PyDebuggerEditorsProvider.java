// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.google.common.annotations.VisibleForTesting;
import consulo.application.ReadAction;
import consulo.language.Language;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.ui.XNamedTreeNode;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.EvaluationMode;
import consulo.execution.debug.evaluation.InlineDebuggerHelper;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.XDebuggerUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.impl.psi.impl.PyCodeFragmentWithHiddenImports;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;


public class PyDebuggerEditorsProvider extends XDebuggerEditorsProvider {
    @Override
    public FileType getFileType() {
        return PythonFileType.INSTANCE;
    }

    @Override
    public Document createDocument(Project project, String text, @Nullable XSourcePosition sourcePosition, EvaluationMode mode) {
        // Consulo: XDebuggerEditorsProvider requires the text variant, it is served by the expression variant below
        return createDocument(project, XDebuggerUtil.getInstance().createExpression(text, PythonLanguage.INSTANCE, null, mode), sourcePosition, mode);
    }

    @Override
    public Document createDocument(Project project,
                                   XExpression expression,
                                            @Nullable XSourcePosition sourcePosition,
                                            EvaluationMode mode) {
        String text = expression.getExpression().trim();
        PyCodeFragmentWithHiddenImports fragment = new PyCodeFragmentWithHiddenImports(project, "fragment.py", text, true);

        // Bind to context
        PsiElement element = getContextElement(project, sourcePosition);
        fragment.setContext(element);

        if (expression.getCustomInfo() != null) {
            fragment.addImportsFromStrings(expression.getCustomInfo().lines().toList());
        }
        return PsiDocumentManager.getInstance(project).getDocument(fragment);
    }

    @Override
    public XExpression createExpression(Project project,
                                        Document document,
                                        @Nullable Language language,
                                        EvaluationMode mode) {
        // Consulo-specific: the watches view asks for the expression from a background action update, outside a read action
        return ReadAction.compute(() -> {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (file instanceof PyCodeFragmentWithHiddenImports fragment) {
                List<PyImportStatementBase> pseudoImports = fragment.getPseudoImports();
                String customInfo = StringUtil.nullize(StringUtil.join(pseudoImports, PsiElement::getText, "\n"));
                return XDebuggerUtil.getInstance().createExpression(fragment.getText(), language, customInfo, mode);
            }
            return super.createExpression(project, document, language, mode);
        });
    }

    @VisibleForTesting
    public static @Nullable PsiElement getContextElement(Project project, XSourcePosition sourcePosition) {
        if (sourcePosition != null) {
            Document document = FileDocumentManager.getInstance().getDocument(sourcePosition.getFile());
            if (document == null) {
                return null;
            }
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (psiFile != null) {
                int offset = sourcePosition.getOffset();
                if (offset >= 0 && offset < document.getTextLength()) {
                    int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
                    do {
                        PsiElement element = psiFile.findElementAt(offset);
                        if (element != null && !(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
                            return PyPsiUtils.getStatement(element);
                        }
                        if (element == null) {
                            return null;
                        }
                        offset = element.getTextRange().getEndOffset();
                    }
                    while (offset < lineEndOffset);
                }
            }
        }
        return null;
    }

    private static final class PyInlineDebuggerHelper extends InlineDebuggerHelper {
        private static final PyInlineDebuggerHelper INSTANCE = new PyInlineDebuggerHelper();

        @Override
        public boolean shouldEvaluateChildrenByDefault(XNamedTreeNode node) {
            return false;
        }
    }

    @Override
    public InlineDebuggerHelper getInlineDebuggerHelper() {
        return PyInlineDebuggerHelper.INSTANCE;
    }
}
