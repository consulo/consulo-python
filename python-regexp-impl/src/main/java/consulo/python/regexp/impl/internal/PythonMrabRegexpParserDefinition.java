// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParser;
import org.intellij.lang.regexp.RegExpParserDefinition;

import java.util.EnumSet;

@ExtensionImpl
public class PythonMrabRegexpParserDefinition extends RegExpParserDefinition {
    private static final IFileElementType FILE = new IFileElementType("PYTHON_MRAB_REGEXP_FILE", PythonMrabRegexpLanguage.INSTANCE);

    @Override
    public Language getLanguage() {
        return PythonMrabRegexpLanguage.INSTANCE;
    }

    // TODO consulo: RegExpParserDefinition#getCapabilities() missing, capabilities are passed to lexer/parser directly
    public EnumSet<RegExpCapability> getCapabilities() {
        return PythonMrabRegexpKt.CAPABILITIES;
    }

    @Override
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new RegExpLexer(getCapabilities());
    }

    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new RegExpParser(getCapabilities());
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new RegExpFile(viewProvider, PythonMrabRegexpLanguage.INSTANCE);
    }
}
