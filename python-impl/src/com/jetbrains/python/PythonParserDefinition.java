package com.jetbrains.python;

import org.jetbrains.annotations.NotNull;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyFileElementType;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFileImpl;

/**
 * @author yole
 * @author Keith Lea
 */
public class PythonParserDefinition implements ParserDefinition {
  private final TokenSet myWhitespaceTokens;
  private final TokenSet myCommentTokens;
  private final TokenSet myStringLiteralTokens;

  public PythonParserDefinition() {
    myWhitespaceTokens = TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
    myCommentTokens = TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
    myStringLiteralTokens = TokenSet.orSet(PyTokenTypes.STRING_NODES, TokenSet.create(PyElementTypes.STRING_LITERAL_EXPRESSION));
  }

  @NotNull
  public Lexer createLexer(Project project, LanguageVersion languageVersion) {
    return new PythonIndentingLexer();
  }

  public IFileElementType getFileNodeType() {
    return PyFileElementType.INSTANCE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return myWhitespaceTokens;
  }

  @NotNull
  public TokenSet getCommentTokens(LanguageVersion languageVersion) {
    return myCommentTokens;
  }

  @NotNull
  public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
    return myStringLiteralTokens;
  }

  @NotNull
  public PsiParser createParser(Project project, LanguageVersion languageVersion) {
    return new PyParser();
  }

  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof PyElementType) {
      PyElementType pyElType = (PyElementType)type;
      return pyElType.createElement(node);
    }
    else if (type instanceof PyStubElementType) {
      return ((PyStubElementType)type).createElement(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    // see LanguageTokenSeparatorGenerator instead
    return SpaceRequirements.MAY;
  }

}
