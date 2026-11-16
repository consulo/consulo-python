// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.jetbrains.python.PyTokenTypes;
import consulo.language.ast.IElementType;
import consulo.language.ast.StringEscapesTokenTypes;
import consulo.util.lang.CharArrayUtil;

import java.util.stream.Stream;

public class PyFStringLiteralLexer extends PyStringLiteralLexerBase {
  public PyFStringLiteralLexer(IElementType fStringTextToken) {
    super(fStringTextToken);
    assert PyTokenTypes.FSTRING_TEXT_TOKENS.contains(fStringTextToken);
  }

  @Override
  protected int locateToken(int start) {
    if (start >= myBufferEnd) {
      return myBufferEnd;
    }
    else if (myBuffer.charAt(start) == '\\') {
      return locateEscapeSequence(start);
    }
    else if (isDoubleCurley()) {
      return start + 2;
    }
    else {
      int nextBackslashOffset = CharArrayUtil.indexOf(myBuffer, "\\", start + 1, myBufferEnd);
      int nextLCurleyOffset = CharArrayUtil.indexOf(myBuffer, "{{", start + 1, myBufferEnd);
      int nextRCurleyOffset = CharArrayUtil.indexOf(myBuffer, "}}", start + 1, myBufferEnd);
      return Stream.of(nextBackslashOffset, nextLCurleyOffset, nextRCurleyOffset)
        .filter(it -> it >= 0)
        .min(Integer::compare)
        .orElse(myBufferEnd);
    }
  }

  @Override
  protected boolean isRaw() {
    return myOriginalLiteralToken == PyTokenTypes.FSTRING_RAW_TEXT;
  }

  @Override
  protected boolean isUnicodeMode() {
    return true;
  }

  @Override
  public int getState() {
    return myBaseLexerState;
  }

  @Override
  protected boolean isEscape() {
    return isDoubleCurley() || super.isEscape();
  }

  @Override
  public IElementType getEscapeSequenceType() {
    return isDoubleCurley() ? StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN : super.getEscapeSequenceType();
  }

  private boolean isDoubleCurley() {
    return myBufferEnd > myStart + 1 &&
           ((myBuffer.charAt(myStart) == '{' && myBuffer.charAt(myStart + 1) == '{') ||
            (myBuffer.charAt(myStart) == '}' && myBuffer.charAt(myStart + 1) == '}'));
  }
}
