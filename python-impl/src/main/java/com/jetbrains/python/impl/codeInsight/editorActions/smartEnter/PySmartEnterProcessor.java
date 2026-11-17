// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.action.SmartEnterProcessor;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.logging.Logger;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.enterProcessors.EnterProcessor;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.enterProcessors.PyCommentBreakerEnterProcessor;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.enterProcessors.PyPlainEnterProcessor;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.GoogleDocStringSectionFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyArgumentListFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyCaseClauseFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyClassFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyCollectionLiteralFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyConditionalStatementPartFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyExceptFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyForPartFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyFunctionFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyMatchStatementFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyMissingBracesFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyParameterListFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyParenthesizedFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyStringLiteralFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyUnconditionalStatementPartFixer;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers.PyWithFixer;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class PySmartEnterProcessor extends SmartEnterProcessor {
    private static final Logger LOG = Logger.getInstance(PySmartEnterProcessor.class);
    private static final List<PyFixer> ourFixers = List.of(
        new PyStringLiteralFixer(),
        new GoogleDocStringSectionFixer(),
        new PyParenthesizedFixer(),
        new PyMissingBracesFixer(),
        new PyConditionalStatementPartFixer(),
        new PyUnconditionalStatementPartFixer(),
        new PyForPartFixer(),
        new PyExceptFixer(),
        new PyArgumentListFixer(),
        new PyParameterListFixer(),
        new PyFunctionFixer(),
        new PyClassFixer(),
        new PyWithFixer(),
        new PyCollectionLiteralFixer(),
        new PyMatchStatementFixer(),
        new PyCaseClauseFixer()
    );
    private static final List<EnterProcessor> ourProcessors = List.of(new PyCommentBreakerEnterProcessor(), new PyPlainEnterProcessor());

    private static class TooManyAttemptsException extends Exception {
    }

    private static void collectAllElements(PsiElement element, List<PsiElement> result, boolean recurse) {
        result.add(0, element);
        if (doNotStep(element)) {
            if (!recurse) {
                return;
            }
            recurse = false;
        }

        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
            if (element instanceof PyStatement && child instanceof PyStatement) {
                continue;
            }
            collectAllElements(child, result, recurse);
        }
    }

    private static boolean doNotStep(PsiElement element) {
        return (element instanceof PyStatementList) || (element instanceof PyStatement);
    }

    private static boolean isModified(Editor editor) {
        Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
        return editor.getDocument().getModificationStamp() != timestamp.longValue();
    }

    private int myFirstErrorOffset = Integer.MAX_VALUE;
    private static final int MAX_ATTEMPTS = 20;
    private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

    @Override
    public boolean process(Project project, Editor editor, PsiFile psiFile) {
        Document document = editor.getDocument();
        String textForRollBack = document.getText();
        int offset = editor.getCaretModel().getOffset();
        try {
            editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument().getModificationStamp());
            myFirstErrorOffset = Integer.MAX_VALUE;
            process(project, editor, psiFile, 0);
        }
        catch (TooManyAttemptsException e) {
            LOG.info(e);
            document.replaceString(0, document.getTextLength(), textForRollBack);
            editor.getCaretModel().moveToOffset(offset);
        }
        finally {
            editor.putUserData(SMART_ENTER_TIMESTAMP, null);
        }
        return true;
    }

    private void process(Project project, Editor editor, PsiFile psiFile, int attempt)
        throws TooManyAttemptsException {
        if (attempt > MAX_ATTEMPTS) {
            throw new TooManyAttemptsException();
        }

        try {
            commit(editor);
            if (myFirstErrorOffset != Integer.MAX_VALUE) {
                editor.getCaretModel().moveToOffset(myFirstErrorOffset);
            }

            //myFirstErrorOffset = Integer.MAX_VALUE;

            PsiElement statementAtCaret = getStatementAtCaret(editor, psiFile);
            if (statementAtCaret == null) {
                if (!new PyCommentBreakerEnterProcessor().doEnter(editor, psiFile, false)) {
                    SmartEnterUtil.plainEnter(editor);
                }
                return;
            }

            List<PsiElement> queue = new ArrayList<>();
            collectAllElements(statementAtCaret, queue, true);
            queue.add(statementAtCaret);

            for (PsiElement element : queue) {
                for (PyFixer fixer : ourFixers) {
                    fixer.apply(editor, this, element);
                    if (LookupManager.getInstance(project).getActiveLookup() != null) {
                        return;
                    }
                    PyPsiUtils.assertValid(element);
                    if (isUncommited(project) || !element.isValid()) {
                        process(project, editor, psiFile, attempt + 1);
                        return;
                    }
                }
            }

            doEnter(statementAtCaret, editor);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    private void doEnter(PsiElement atCaret, Editor editor) {
        if (myFirstErrorOffset != Integer.MAX_VALUE) {
            editor.getCaretModel().moveToOffset(myFirstErrorOffset);
            return;
        }
        commit(editor);

        for (EnterProcessor enterProcessor : ourProcessors) {
            if (enterProcessor.doEnter(editor, atCaret, isModified(editor))) {
                return;
            }
        }

        if (!isModified(editor)) {
            SmartEnterUtil.plainEnter(editor);
        }
        else {
            if (myFirstErrorOffset == Integer.MAX_VALUE) {
                editor.getCaretModel().moveToOffset(atCaret.getTextRange().getEndOffset());
            }
            else {
                editor.getCaretModel().moveToOffset(myFirstErrorOffset);
            }
        }
    }

    @Override
    protected @Nullable PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
        PsiElement statementAtCaret = super.getStatementAtCaret(editor, psiFile);

        if (statementAtCaret instanceof PsiWhiteSpace) {
            return null;
        }
        if (statementAtCaret == null) {
            return null;
        }

        PyStatementList statementList = PsiTreeUtil.getParentOfType(statementAtCaret, PyStatementList.class, false);
        if (statementList != null) {
            for (PyStatement statement : statementList.getStatements()) {
                if (PsiTreeUtil.isAncestor(statement, statementAtCaret, true)) {
                    return statement;
                }
            }
        }
        else {
            PyFile file = PsiTreeUtil.getParentOfType(statementAtCaret, PyFile.class, false);
            if (file != null) {
                for (PyStatement statement : file.getStatements()) {
                    if (PsiTreeUtil.isAncestor(statement, statementAtCaret, true)) {
                        return statement;
                    }
                }
            }
        }
        return null;
    }

    public void registerUnresolvedError(int offset) {
        if (offset < myFirstErrorOffset) {
            myFirstErrorOffset = offset;
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
