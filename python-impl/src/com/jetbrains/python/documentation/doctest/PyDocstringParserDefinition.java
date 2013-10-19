package com.jetbrains.python.documentation.doctest;

import org.jetbrains.annotations.NotNull;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonParserDefinition;

/**
 * User : ktisha
 */
public class PyDocstringParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_DOCSTRING_FILE = new PyDocstringFileElementType(PyDocstringLanguageDialect
                                                                                                .getInstance());

  @NotNull
  public Lexer createLexer(Project project, LanguageVersion languageVersion) {
    return new PyDocstringLexer();
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project, LanguageVersion languageVersion) {
    return new PyDocstringParser();
  }


  @NotNull
  @Override
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return TokenSet.orSet(super.getWhitespaceTokens(languageVersion), TokenSet.create(PyDocstringTokenTypes.DOTS));
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PYTHON_DOCSTRING_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyDocstringFile(viewProvider);
  }
}
