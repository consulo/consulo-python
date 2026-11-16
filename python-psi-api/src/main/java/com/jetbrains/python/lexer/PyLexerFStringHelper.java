// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import consulo.language.ast.IElementType;
import consulo.util.collection.Stack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;


public class PyLexerFStringHelper {
  private final FlexLexerEx myLexer;
  private final Stack<FStringState> myFStringStates = new Stack<>();

  public PyLexerFStringHelper(FlexLexerEx lexer) {
    myLexer = lexer;
  }

  public IElementType handleFStringStartInFragment() {
    String prefixAndQuotes = myLexer.yytext().toString();
    return pushFString(prefixAndQuotes);
  }

  public IElementType handleFStringStart() {
    return pushFString(myLexer.yytext().toString());
  }

  private PyElementType pushFString(String prefixAndQuotes) {
    int prefixLength = PyStringLiteralUtil.getPrefixLength(prefixAndQuotes);
    String openingQuotes = prefixAndQuotes.substring(prefixLength);
    String prefix = prefixAndQuotes.substring(0, prefixLength);
    myFStringStates.push(new FStringState(myLexer.yystate(), myLexer.getTokenStart(), prefix, openingQuotes));
    myLexer.yybegin(_PythonLexer.FSTRING);
    return PyTokenTypes.FSTRING_START;
  }

  public IElementType handleFStringEnd() {
    IElementType textType = getTextTokenType();
    Terminator terminator = findFStringTerminator(myLexer.yytext().toString());
    return terminator.offset() == 0 ? Objects.requireNonNull(terminator.type()) : textType;
  }

  public IElementType handleFragmentStart() {
    myFStringStates.peek().fragmentStates.push(new FragmentState(myLexer.yystate(), myLexer.getTokenStart()));
    myLexer.yybegin(_PythonLexer.FSTRING_FRAGMENT);
    return PyTokenTypes.FSTRING_FRAGMENT_START;
  }

  public IElementType handleFragmentEnd() {
    FragmentState state = myFStringStates.peek().fragmentStates.pop();
    myLexer.yybegin(state.oldState);
    return PyTokenTypes.FSTRING_FRAGMENT_END;
  }

  public IElementType handleLeftBracketInFragment(IElementType type) {
    myFStringStates.peek().fragmentStates.peek().braceBalance++;
    return type;
  }

  public IElementType handleRightBracketInFragment(IElementType type) {
    FragmentState activeFragmentState = myFStringStates.peek().fragmentStates.peek();
    if (activeFragmentState.braceBalance == 0 && type == PyTokenTypes.RBRACE) {
      return handleFragmentEnd();
    }
    else {
      activeFragmentState.braceBalance--;
      return type;
    }
  }

  public IElementType handleColonInFragment() {
    if (myFStringStates.peek().fragmentStates.peek().braceBalance == 0) {
      myLexer.yybegin(_PythonLexer.FSTRING_FRAGMENT_FORMAT);
      return PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START;
    }
    else {
      return PyTokenTypes.COLON;
    }
  }

  public IElementType handleColonEqInFragment() {
    if (myFStringStates.peek().fragmentStates.peek().braceBalance == 0) {
      myLexer.yybegin(_PythonLexer.FSTRING_FRAGMENT_FORMAT);
      myLexer.yypushback(1);
      return PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START;
    }
    else {
      return PyTokenTypes.COLONEQ;
    }
  }

  public IElementType handleLineBreakInFragment() {
    return PyTokenTypes.LINE_BREAK;
  }

  public IElementType handleLineBreakInLiteralText() {
    String text = myLexer.yytext().toString();
    Terminator terminator = findFStringTerminator(text);
    if (terminator.offset() == text.length()) {
      return getTextTokenType();
    }
    return PyTokenTypes.LINE_BREAK;
  }

  public IElementType handleStringLiteral(IElementType stringLiteralType) {
    return stringLiteralType;
  }

  private Terminator findFStringTerminator(String text) {
    String topmostFStringQuotes = myFStringStates.peek().openingQuotes;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (c == '\n') {
        if (topmostFStringQuotes.length() == 1) {
          if (i == 0) {
            // Terminate all f-strings and insert STATEMENT_BREAK at this point
            dropFStringStateWithAllNested(0);
          }
          pushBackToOrConsumeMatch(i, 1);
          return new Terminator(PyTokenTypes.LINE_BREAK, i);
        }
      }
      else {
        String nextThree = text.substring(i, Math.min(text.length(), i + 3));
        if (nextThree.startsWith(topmostFStringQuotes)) {
          if (i == 0) {
            dropFStringStateWithAllNested(myFStringStates.size() - 1);
          }
          pushBackToOrConsumeMatch(i, topmostFStringQuotes.length());
          return new Terminator(PyTokenTypes.FSTRING_END, i);
        }
      }
      i++;
    }
    return new Terminator(null, text.length());
  }

  private void dropFStringStateWithAllNested(int fStringIndex) {
    myLexer.yybegin(myFStringStates.get(fStringIndex).oldState);
    myFStringStates.subList(fStringIndex, myFStringStates.size()).clear();
  }

  /**
   * Split the current token at its fragment.
   * <p>
   * If it's a prefix match, e.g. the first quote `'` of a token `'text'`,
   * leave only its own text and return everything that follows back to the input
   * stream to be tokenized again.
   * If it's a middle match, e.g. the same first quote in a token `r'foo'`, leave only
   * the text that precedes it (`r`) and tokenize everything that follows (including
   * the quote itself) again.
   */
  private void pushBackToOrConsumeMatch(int matchOffset, int matchSize) {
    if (matchOffset == 0) {
      myLexer.yypushback(myLexer.yylength() - matchSize);
    }
    else {
      myLexer.yypushback(myLexer.yylength() - matchOffset);
    }
  }

  public void reset() {
    // There is no need to be smarter about it, since LexerEditorHighlighter always resets
    // the lexer state to YYINITIAL where there can't be any f-strings.
    if (!myFStringStates.isEmpty()) {
      dropFStringStateWithAllNested(0);
    }
  }

  public IElementType getTextTokenType() {
    assert !myFStringStates.isEmpty();
    if (PyStringLiteralUtil.isRawPrefix(myFStringStates.peek().prefix)) {
      return PyTokenTypes.FSTRING_RAW_TEXT;
    }
    return PyTokenTypes.FSTRING_TEXT;
  }

  private record Terminator(@Nullable IElementType type, int offset) {
  }

  private static final class FStringState {
    final int oldState;
    final int offset;
    final String prefix;
    final String openingQuotes;
    final Stack<FragmentState> fragmentStates = new Stack<>();

    FStringState(int oldState, int offset, String prefix, String openingQuotes) {
      this.oldState = oldState;
      this.offset = offset;
      this.prefix = prefix;
      this.openingQuotes = openingQuotes;
    }
  }

  private static final class FragmentState {
    final int oldState;
    final int offset;
    int braceBalance = 0;

    FragmentState(int oldState, int offset) {
      this.oldState = oldState;
      this.offset = offset;
    }
  }
}
