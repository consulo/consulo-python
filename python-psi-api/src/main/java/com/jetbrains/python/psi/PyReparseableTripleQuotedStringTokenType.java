package com.jetbrains.python.psi;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import consulo.language.ast.IElementType;

public class PyReparseableTripleQuotedStringTokenType extends PyReparseableTokenTypeWithSimpleCheck {

  public PyReparseableTripleQuotedStringTokenType(String debugName) {
    super(debugName);
  }

  @Override
  public boolean isReparseable(String newText) {
    if (!isTripleQuotedString(newText)) { // fail-fast
      return false;
    }
    PythonLexer lexer = new PythonLexer();
    lexer.start(newText);
    IElementType firstTokenType = lexer.getTokenType();
    lexer.advance();
    IElementType nextTokenType = lexer.getTokenType();
    return firstTokenType == PyTokenTypes.DOCSTRING && nextTokenType == null;
  }

  private static boolean isTripleQuotedString(String newText) {
    return newText.length() >= 6
           && ((newText.startsWith("\"\"\"") && newText.endsWith("\"\"\""))
               || (newText.startsWith("'''") && newText.endsWith("'''")));
  }
}
