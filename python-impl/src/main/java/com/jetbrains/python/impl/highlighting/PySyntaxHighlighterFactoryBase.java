// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.highlighting;

import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.parsing.console.PyConsoleHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.Language;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.project.Project;
import consulo.util.collection.FactoryMap;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class PySyntaxHighlighterFactoryBase extends SyntaxHighlighterFactory {
    private final Map<LanguageLevel, PyHighlighter> myMap = FactoryMap.create(key -> new PyHighlighter(key));

    private final Map<LanguageLevel, PyHighlighter> myConsoleMap = FactoryMap.create(key -> new PyHighlighter(key) {
        @Override
        protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
            return new PyConsoleHighlightingLexer(languageLevel);
        }
    });

    @Override
    public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        LanguageLevel level = getLanguageLevel(project, virtualFile);
        if (useConsoleLexer(project, virtualFile)) {
            return myConsoleMap.get(level);
        }
        return getSyntaxHighlighterForLanguageLevel(level);
    }

    /**
     * Returns a syntax highlighter for Python console.
     */
    public SyntaxHighlighter getConsoleSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        LanguageLevel level = getLanguageLevel(project, virtualFile);
        return myConsoleMap.get(level);
    }

    /**
     * Returns a syntax highlighter targeting the specified version of Python.
     */
    public SyntaxHighlighter getSyntaxHighlighterForLanguageLevel(LanguageLevel level) {
        return myMap.get(level);
    }

    private static LanguageLevel getLanguageLevel(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        return project != null && virtualFile != null
            ? PyLanguageFacade.getInstance().getEffectiveLanguageLevel(project, virtualFile)
            : LanguageLevel.getDefault();
    }

    protected boolean useConsoleLexer(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        return false;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
