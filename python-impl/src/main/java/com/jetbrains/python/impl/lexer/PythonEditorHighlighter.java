// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.lexer;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import consulo.codeEditor.HighlighterClient;
import consulo.colorScheme.EditorColorsScheme;
import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.util.TextRange;
import consulo.language.editor.highlight.LexerEditorHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.lexer.LayeredLexer;
import consulo.language.lexer.Lexer;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

public final class PythonEditorHighlighter extends LexerEditorHighlighter {

    public PythonEditorHighlighter(EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file) {
        super(SyntaxHighlighterFactory.getSyntaxHighlighter(file != null ? file.getFileType() : PythonFileType.INSTANCE,
                project,
                file),
            scheme);
    }

    private Boolean hadUnicodeImport = false;

    public static final Key<Boolean> KEY = new Key<>("python.future.import");

    @Override
    public void documentChanged(DocumentEvent e) {
        synchronized (this) {
            Document document = e.getDocument();
            Lexer l = getLexer();
            // if the document been changed before "from __future__ import unicode_literals"
            // we should update the whole document
            if (l instanceof LayeredLexer) {
                Lexer delegate = ((LayeredLexer) l).getDelegate();
                int offset = e.getOffset();
                int lineNumber = document.getLineNumber(offset);
                TextRange tr = new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
                document.putUserData(KEY, !document.getText(tr).contains(PyNames.UNICODE_LITERALS));
                Boolean hasUnicodeImport = document.getUserData(KEY);
                if (delegate instanceof PythonHighlightingLexer &&
                    (((PythonHighlightingLexer) delegate).getImportOffset() > e.getOffset()
                        || hasUnicodeImport != hadUnicodeImport)) {
                    ((PythonHighlightingLexer) delegate).clearState(e.getDocument().getTextLength());
                    setText(document.getCharsSequence());
                }
                else {
                    super.documentChanged(e);
                }
            }
            else {
                super.documentChanged(e);
            }
        }
    }

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
        Document document = e.getDocument();
        hadUnicodeImport = document.getUserData(KEY);
    }

    @Override
    public void setEditor(HighlighterClient editor) {
        Lexer l = getLexer();
        if (l instanceof LayeredLexer) {
            editor.getDocument().putUserData(KEY, !editor.getDocument().getText().contains(PyNames.UNICODE_LITERALS));
        }
        super.setEditor(editor);
    }
}
