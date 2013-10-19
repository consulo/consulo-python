package com.jetbrains.python.codeInsight.regexp;

import java.util.EnumSet;

import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParser;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;

/**
 * @author yole
 */
public class PythonVerboseRegexpParserDefinition extends PythonRegexpParserDefinition {
  public static final IFileElementType VERBOSE_PYTHON_REGEXP_FILE = new IFileElementType("VERBOSE_PYTHON_REGEXP_FILE", PythonVerboseRegexpLanguage.INSTANCE);
  private final EnumSet<RegExpCapability> VERBOSE_CAPABILITIES;

  public PythonVerboseRegexpParserDefinition() {
    VERBOSE_CAPABILITIES = EnumSet.copyOf(CAPABILITIES);
    VERBOSE_CAPABILITIES.add(RegExpCapability.COMMENT_MODE);
  }

  @NotNull
  public Lexer createLexer(Project project, LanguageVersion languageVersion) {
    return new RegExpLexer(VERBOSE_CAPABILITIES);
  }

  @Override
  public PsiParser createParser(Project project, LanguageVersion languageVersion) {
    return new RegExpParser(VERBOSE_CAPABILITIES);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return VERBOSE_PYTHON_REGEXP_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, PythonVerboseRegexpLanguage.INSTANCE);
  }
}
