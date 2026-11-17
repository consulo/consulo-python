// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParser;
import org.intellij.lang.regexp.RegExpParserDefinition;

import java.util.EnumSet;

import static org.intellij.lang.regexp.RegExpCapability.DANGLING_METACHARACTERS;
import static org.intellij.lang.regexp.RegExpCapability.MIN_OCTAL_3_DIGITS;
import static org.intellij.lang.regexp.RegExpCapability.OCTAL_NO_LEADING_ZERO;
import static org.intellij.lang.regexp.RegExpCapability.OMIT_BOTH_NUMBERS_IN_QUANTIFIERS;
import static org.intellij.lang.regexp.RegExpCapability.OMIT_NUMBERS_IN_QUANTIFIERS;

@ExtensionImpl
public class PythonRegexpParserDefinition extends RegExpParserDefinition {
    public static final IFileElementType PYTHON_REGEXP_FILE = new IFileElementType("PYTHON_REGEXP_FILE", PythonRegexpLanguage.INSTANCE);
    protected final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(DANGLING_METACHARACTERS,
        OCTAL_NO_LEADING_ZERO,
        OMIT_NUMBERS_IN_QUANTIFIERS,
        OMIT_BOTH_NUMBERS_IN_QUANTIFIERS,
        MIN_OCTAL_3_DIGITS
        // TODO consulo: RegExpCapability.SHORTHAND_CLASS_RANGE_START_ERROR missing
        // TODO consulo: RegExpCapability.SHORTHAND_CLASS_RANGE_END_ERROR missing
    );

    @Override
    public Language getLanguage() {
        return PythonRegexpLanguage.INSTANCE;
    }

    // TODO consulo: RegExpParserDefinition#getCapabilities() missing, capabilities are passed to lexer/parser directly
    public EnumSet<RegExpCapability> getCapabilities() {
        return CAPABILITIES;
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
        return PYTHON_REGEXP_FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new DjangoRegexpFile(viewProvider);
    }
}
