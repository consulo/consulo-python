/*
 * Copyright 2006 Dmitry Jemerov (yole)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.consulo.python;

import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class PythonIndentingLexer extends PythonLexer {
	private Stack<Integer> _indentStack = new Stack<Integer>();
	private int _braceLevel;
	private boolean _lineHasSignificantTokens;
	private List<PendingToken> _tokenQueue = new ArrayList<PendingToken>();

	private static final boolean DUMP_TOKENS = false;

	public PythonIndentingLexer() {
		super();
	}

	public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
		if ((startOffset != 0) || (initialState != 0)) {
			throw new RuntimeException("Indenting lexer does not support incremental lexing");
		}
		super.start(buffer, startOffset, endOffset, initialState);
		this._indentStack.clear();
		this._indentStack.push(0);
		this._braceLevel = 0;
		adjustBraceLevel();
		this._lineHasSignificantTokens = false;
		checkSignificantTokens();
	}

	private void adjustBraceLevel() {
		if (PythonTokenSets.OPEN_BRACES.contains(getTokenType())) {
			this._braceLevel += 1;
		} else if (PythonTokenSets.CLOSE_BRACES.contains(getTokenType())) {
			this._braceLevel -= 1;
		}
	}

	@Override
	public IElementType getTokenType() {
		if (this._tokenQueue.size() > 0) {
			return this._tokenQueue.get(0).getType();
		}
		return super.getTokenType();
	}

	@Override
	public int getTokenStart() {
		if (this._tokenQueue.size() > 0) {
			return this._tokenQueue.get(0).getStart();
		}
		return super.getTokenStart();
	}

	@Override
	public int getTokenEnd() {
		if (this._tokenQueue.size() > 0) {
			return this._tokenQueue.get(0).getEnd();
		}
		return super.getTokenEnd();
	}

	private void advanceBase() {
		super.advance();
		checkSignificantTokens();
	}

	private void checkSignificantTokens() {
		IElementType tokenType = super.getTokenType();
		if ((!PythonTokenSets.WHITESPACE_OR_LINEBREAK.contains(tokenType)) && (tokenType != PythonTokenSets.END_OF_LINE_COMMENT)) {
			this._lineHasSignificantTokens = true;
		}
	}

	@Override
	public void advance() {
		if (this._tokenQueue.size() > 0) {
			this._tokenQueue.remove(0);
		} else {
			advanceBase();
			int tokenStart = super.getTokenStart();
			if (super.getTokenType() == PyTokenTypes.LINE_BREAK) {
				processLineBreak(tokenStart);
			} else if (super.getTokenType() == PyTokenTypes.BACKSLASH) {
				processBackslash(tokenStart);
			} else if (super.getTokenType() == PyTokenTypes.SPACE) {
				processSpace();
			}
		}

		adjustBraceLevel();
	}

	private void processSpace() {
		int start = super.getTokenStart();
		int end = super.getTokenEnd();
		while (super.getTokenType() == PyTokenTypes.SPACE) {
			end = super.getTokenEnd();
			advanceBase();
		}
		if (super.getTokenType() == PyTokenTypes.LINE_BREAK) {
			processLineBreak(start);
		} else if (super.getTokenType() == PyTokenTypes.BACKSLASH) {
			processBackslash(start);
		} else
			this._tokenQueue.add(new PendingToken(PyTokenTypes.SPACE, start, end));
	}

	private void processBackslash(int tokenStart) {
		PendingToken backslashToken = new PendingToken(super.getTokenType(), tokenStart, super.getTokenEnd());

		this._tokenQueue.add(backslashToken);
		advanceBase();
		while (PythonTokenSets.WHITESPACE.contains(super.getTokenType())) {
			pushCurrentToken();
			advanceBase();
		}
		if (super.getTokenType() == PythonTokenSets.LINE_BREAK) {
			backslashToken.setType(PythonTokenSets.SPACE);
			processInsignificantLineBreak(super.getTokenStart());
		}
	}

	private void processLineBreak(int startPos) {
		if (this._braceLevel == 0) {
			if (this._lineHasSignificantTokens) {
				pushToken(PythonTokenSets.STATEMENT_BREAK, startPos, startPos);
			}
			this._lineHasSignificantTokens = false;
			advanceBase();
			processIndent(startPos);
		} else {
			processInsignificantLineBreak(startPos);
		}
	}

	private void processInsignificantLineBreak(int startPos) {
		int end = super.getTokenEnd();
		advanceBase();

		while ((super.getTokenType() == PyTokenTypes.SPACE) || (super.getTokenType() == PyTokenTypes.LINE_BREAK)) {
			end = super.getTokenEnd();
			advanceBase();
		}
		this._tokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, startPos, end));
	}

	private void processIndent(int whiteSpaceStart) {
		int lastIndent = this._indentStack.peek();
		int indent = getNextLineIndent();

		if (super.getTokenType() == PythonTokenSets.END_OF_LINE_COMMENT) {
			indent = lastIndent;
		}
		int whiteSpaceEnd = super.getTokenType() == null ? super.getBufferEnd() : super.getTokenStart();
		if (indent > lastIndent) {
			this._indentStack.push(indent);
			this._tokenQueue.add(new PendingToken(PythonTokenSets.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
			this._tokenQueue.add(new PendingToken(PythonTokenSets.INDENT, whiteSpaceEnd, whiteSpaceEnd));
		} else if (indent < lastIndent) {
			while (indent < lastIndent) {
				this._indentStack.pop();
				lastIndent = this._indentStack.peek();
				if (indent > lastIndent) {
					this._tokenQueue.add(new PendingToken(PythonTokenSets.INCONSISTENT_DEDENT, whiteSpaceStart, whiteSpaceStart));
				}
				this._tokenQueue.add(new PendingToken(PythonTokenSets.DEDENT, whiteSpaceStart, whiteSpaceStart));
			}
			this._tokenQueue.add(new PendingToken(PythonTokenSets.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
		} else {
			this._tokenQueue.add(new PendingToken(PythonTokenSets.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
		}
	}

	private int getNextLineIndent() {
		int indent = 0;
		while ((super.getTokenType() != null) && (PythonTokenSets.WHITESPACE_OR_LINEBREAK.contains(super.getTokenType()))) {
			if (super.getTokenType() == PythonTokenSets.TAB) {
				indent = (indent / 8 + 1) * 8;
			} else if (super.getTokenType() == PythonTokenSets.SPACE) {
				indent++;
			} else if (super.getTokenType() == PythonTokenSets.LINE_BREAK) {
				indent = 0;
			}
			advanceBase();
		}
		if (super.getTokenType() == null) {
			return 0;
		}
		return indent;
	}

	private void pushCurrentToken() {
		this._tokenQueue.add(new PendingToken(super.getTokenType(), super.getTokenStart(), super.getTokenEnd()));
	}

	private void pushToken(IElementType type, int start, int end) {
		this._tokenQueue.add(new PendingToken(type, start, end));
	}

	private class PendingToken {
		private IElementType _type;
		private int _start;
		private int _end;

		public PendingToken(IElementType type, int start, int end) {
			this._type = type;
			this._start = start;
			this._end = end;
		}

		public IElementType getType() {
			return this._type;
		}

		public int getStart() {
			return this._start;
		}

		public int getEnd() {
			return this._end;
		}

		public void setType(IElementType type) {
			this._type = type;
		}
	}
}