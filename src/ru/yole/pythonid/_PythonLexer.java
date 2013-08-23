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

package ru.yole.pythonid;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

class _PythonLexer
		implements FlexLexer {
	private static final int ZZ_BUFFERSIZE = 16384;
	public static final int YYINITIAL = 0;
	private static final String ZZ_CMAP_PACKED = "";
	private static final char[] ZZ_CMAP = zzUnpackCMap("");

	private static final int[] ZZ_ACTION = zzUnpackAction();
	private static final String ZZ_ACTION_PACKED_0 = "";
	private static final int[] ZZ_ROWMAP = zzUnpackRowMap();
	private static final String ZZ_ROWMAP_PACKED_0 = "";
	private static final int[] ZZ_TRANS = zzUnpackTrans();
	private static final String ZZ_TRANS_PACKED_0 = "";
	private static final int ZZ_UNKNOWN_ERROR = 0;
	private static final int ZZ_NO_MATCH = 1;
	private static final int ZZ_PUSHBACK_2BIG = 2;
	private static final char[] EMPTY_BUFFER = new char[0];
	private static final int YYEOF = -1;
	private static Reader zzReader = null;

	private static final String[] ZZ_ERROR_MSG = {"Unkown internal scanner error", "Error: could not match input", "Error: pushback value was too large"};

	private static final int[] ZZ_ATTRIBUTE = zzUnpackAttribute();
	private static final String ZZ_ATTRIBUTE_PACKED_0 = "";
	private int zzState;
	private int zzLexicalState = 0;

	private CharSequence zzBuffer = "";
	private int zzMarkedPos;
	private int zzPushbackPos;
	private int zzCurrentPos;
	private int zzStartRead;
	private int zzEndRead;
	private boolean zzAtBOL = true;
	private boolean zzAtEOF;
	private boolean zzEOFDone;
	private PyTokenTypes tokenTypes = null;

	private static int[] zzUnpackAction() {
		int[] result = new int['Ë'];
		int offset = 0;
		offset = zzUnpackAction("", offset, result);
		return result;
	}

	private static int zzUnpackAction(String packed, int offset, int[] result) {
		int i = 0;
		int j = offset;
		int l = packed.length();
		int count;
		for (; i < l;
			 count > 0) {
			count = packed.charAt(i++);
			int value = packed.charAt(i++);
			result[(j++)] = value;
			count--;
		}
		return j;
	}

	private static int[] zzUnpackRowMap() {
		int[] result = new int['Ë'];
		int offset = 0;
		offset = zzUnpackRowMap("", offset, result);
		return result;
	}

	private static int zzUnpackRowMap(String packed, int offset, int[] result) {
		int i = 0;
		int j = offset;
		int l = packed.length();
		while (i < l) {
			int high = packed.charAt(i++) << '\020';
			result[(j++)] = (high | packed.charAt(i++));
		}
		return j;
	}

	private static int[] zzUnpackTrans() {
		int[] result = new int[9179];
		int offset = 0;
		offset = zzUnpackTrans("", offset, result);
		return result;
	}

	private static int zzUnpackTrans(String packed, int offset, int[] result) {
		int i = 0;
		int j = offset;
		int l = packed.length();
		int count;
		for (; i < l;
			 count > 0) {
			count = packed.charAt(i++);
			int value = packed.charAt(i++);
			value--;
			result[(j++)] = value;
			count--;
		}
		return j;
	}

	private static int[] zzUnpackAttribute() {
		int[] result = new int['Ë'];
		int offset = 0;
		offset = zzUnpackAttribute("", offset, result);
		return result;
	}

	private static int zzUnpackAttribute(String packed, int offset, int[] result) {
		int i = 0;
		int j = offset;
		int l = packed.length();
		int count;
		for (; i < l;
			 count > 0) {
			count = packed.charAt(i++);
			int value = packed.charAt(i++);
			result[(j++)] = value;
			count--;
		}
		return j;
	}

	void setLanguage(PythonLanguage language) {
		this.tokenTypes = language.getTokenTypes();
	}

	_PythonLexer(Reader in) {
		zzReader = in;
	}

	_PythonLexer(InputStream in) {
		this(new InputStreamReader(in));
	}

	private static char[] zzUnpackCMap(String packed) {
		char[] map = new char[65536];
		int i = 0;
		int j = 0;
		int count;
		for (; i < 168;
			 count > 0) {
			count = packed.charAt(i++);
			char value = packed.charAt(i++);
			map[(j++)] = value;
			count--;
		}
		return map;
	}

	public final int getTokenStart() {
		return this.zzStartRead;
	}

	public final int getTokenEnd() {
		return getTokenStart() + yylength();
	}

	public void reset(CharSequence buffer, int initialState) {
		this.zzBuffer = buffer;
		this.zzCurrentPos = (this.zzMarkedPos = this.zzStartRead = 0);
		this.zzPushbackPos = 0;
		this.zzAtEOF = false;
		this.zzAtBOL = true;
		this.zzEndRead = buffer.length();
		yybegin(initialState);
	}

	private boolean zzRefill()
			throws IOException {
		return true;
	}

	public final int yystate() {
		return this.zzLexicalState;
	}

	public final void yybegin(int newState) {
		this.zzLexicalState = newState;
	}

	public final CharSequence yytext() {
		return this.zzBuffer.subSequence(this.zzStartRead, this.zzMarkedPos);
	}

	public final char yycharat(int pos) {
		return this.zzBuffer.charAt(this.zzStartRead + pos);
	}

	public final int yylength() {
		return this.zzMarkedPos - this.zzStartRead;
	}

	private void zzScanError(int errorCode) {
		String message;
		try {
			message = ZZ_ERROR_MSG[errorCode];
		} catch (ArrayIndexOutOfBoundsException e) {
			message = ZZ_ERROR_MSG[0];
		}

		throw new Error(message);
	}

	public void yypushback(int number) {
		if (number > yylength()) {
			zzScanError(2);
		}
		this.zzMarkedPos -= number;
	}

	private void zzDoEOF() {
		if (!this.zzEOFDone)
			this.zzEOFDone = true;
	}

	public IElementType advance()
			throws IOException {
		int zzEndReadL = this.zzEndRead;
		CharSequence zzBufferL = this.zzBuffer;
		char[] zzCMapL = ZZ_CMAP;

		int[] zzTransL = ZZ_TRANS;
		int[] zzRowMapL = ZZ_ROWMAP;
		int[] zzAttrL = ZZ_ATTRIBUTE;
		while (true) {
			int zzMarkedPosL = this.zzMarkedPos;

			int zzAction = -1;

			int zzCurrentPosL = this.zzCurrentPos = this.zzStartRead = zzMarkedPosL;

			this.zzState = this.zzLexicalState;
			int zzInput;
			while (true) {
				int zzInput;
				if (zzCurrentPosL < zzEndReadL) {
					zzInput = zzBufferL.charAt(zzCurrentPosL++);
				} else {
					if (this.zzAtEOF) {
						int zzInput = -1;
						break;
					}

					this.zzCurrentPos = zzCurrentPosL;
					this.zzMarkedPos = zzMarkedPosL;
					boolean eof = zzRefill();

					zzCurrentPosL = this.zzCurrentPos;
					zzMarkedPosL = this.zzMarkedPos;
					zzBufferL = this.zzBuffer;
					zzEndReadL = this.zzEndRead;
					if (eof) {
						int zzInput = -1;
						break;
					}

					zzInput = zzBufferL.charAt(zzCurrentPosL++);
				}

				int zzNext = zzTransL[(zzRowMapL[this.zzState] + zzCMapL[zzInput])];
				if (zzNext == -1) break;
				this.zzState = zzNext;

				int zzAttributes = zzAttrL[this.zzState];
				if ((zzAttributes & 0x1) == 1) {
					zzAction = this.zzState;
					zzMarkedPosL = zzCurrentPosL;
					if ((zzAttributes & 0x8) == 8) {
						break;
					}
				}
			}

			this.zzMarkedPos = zzMarkedPosL;

			switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
				case 23:
					return this.tokenTypes.TILDE;
				case 87:
					break;
				case 20:
					return this.tokenTypes.XOR;
				case 88:
					break;
				case 76:
					return this.tokenTypes.PRINT_KEYWORD;
				case 89:
					break;
				case 16:
					return this.tokenTypes.DIV;
				case 90:
					break;
				case 29:
					return this.tokenTypes.RBRACE;
				case 91:
					break;
				case 55:
					return this.tokenTypes.NE_OLD;
				case 92:
					break;
				case 17:
					return this.tokenTypes.PERC;
				case 93:
					break;
				case 25:
					return this.tokenTypes.RPAR;
				case 94:
					break;
				case 52:
					return this.tokenTypes.GE;
				case 95:
					break;
				case 38:
					return this.tokenTypes.OR_KEYWORD;
				case 96:
					break;
				case 2:
					return this.tokenTypes.INTEGER_LITERAL;
				case 97:
					break;
				case 7:
					return this.tokenTypes.MINUS;
				case 98:
					break;
				case 6:
					return this.tokenTypes.DOT;
				case 99:
					break;
				case 68:
					return this.tokenTypes.ELSE_KEYWORD;
				case 100:
					break;
				case 28:
					return this.tokenTypes.LBRACE;
				case 101:
					break;
				case 24:
					return this.tokenTypes.LPAR;
				case 102:
					break;
				case 14:
					return this.tokenTypes.EQ;
				case 103:
					break;
				case 35:
					return this.tokenTypes.FLOAT_LITERAL;
				case 104:
					break;
				case 40:
					return this.tokenTypes.IS_KEYWORD;
				case 105:
					break;
				case 10:
					return this.tokenTypes.SPACE;
				case 106:
					break;
				case 75:
					return this.tokenTypes.CLASS_KEYWORD;
				case 107:
					break;
				case 37:
					return this.tokenTypes.MINUSEQ;
				case 108:
					break;
				case 5:
					return this.tokenTypes.LINE_BREAK;
				case 109:
					break;
				case 9:
					return this.tokenTypes.BACKSLASH;
				case 110:
					break;
				case 27:
					return this.tokenTypes.RBRACKET;
				case 111:
					break;
				case 66:
					return this.tokenTypes.GTGTEQ;
				case 112:
					break;
				case 8:
					return this.tokenTypes.STRING_LITERAL;
				case 113:
					break;
				case 32:
					return this.tokenTypes.COLON;
				case 114:
					break;
				case 65:
					return this.tokenTypes.FLOORDIVEQ;
				case 115:
					break;
				case 80:
					return this.tokenTypes.EXCEPT_KEYWORD;
				case 116:
					break;
				case 41:
					return this.tokenTypes.IF_KEYWORD;
				case 117:
					break;
				case 70:
					return this.tokenTypes.EXEC_KEYWORD;
				case 118:
					break;
				case 72:
					return this.tokenTypes.PASS_KEYWORD;
				case 119:
					break;
				case 77:
					return this.tokenTypes.YIELD_KEYWORD;
				case 120:
					break;
				case 15:
					return this.tokenTypes.MULT;
				case 121:
					break;
				case 12:
					return this.tokenTypes.FORMFEED;
				case 122:
					break;
				case 67:
					return this.tokenTypes.LTLTEQ;
				case 123:
					break;
				case 1:
					return this.tokenTypes.BAD_CHARACTER;
				case 124:
					break;
				case 22:
					return this.tokenTypes.LT;
				case 125:
					break;
				case 85:
					return this.tokenTypes.FINALLY_KEYWORD;
				case 126:
					break;
				case 58:
					return this.tokenTypes.AND_KEYWORD;
				case 127:
					break;
				case 26:
					return this.tokenTypes.LBRACKET;
				case 128:
					break;
				case 44:
					return this.tokenTypes.MULTEQ;
				case 129:
					break;
				case 63:
					return this.tokenTypes.FOR_KEYWORD;
				case 130:
					break;
				case 59:
					return this.tokenTypes.NOT_KEYWORD;
				case 131:
					break;
				case 45:
					return this.tokenTypes.EXP;
				case 132:
					break;
				case 13:
					return this.tokenTypes.PLUS;
				case 133:
					break;
				case 31:
					return this.tokenTypes.COMMA;
				case 134:
					break;
				case 43:
					return this.tokenTypes.EQEQ;
				case 135:
					break;
				case 33:
					return this.tokenTypes.TICK;
				case 136:
					break;
				case 79:
					return this.tokenTypes.ASSERT_KEYWORD;
				case 137:
					break;
				case 74:
					return this.tokenTypes.BREAK_KEYWORD;
				case 138:
					break;
				case 34:
					return this.tokenTypes.SEMICOLON;
				case 139:
					break;
				case 84:
					return this.tokenTypes.GLOBAL_KEYWORD;
				case 140:
					break;
				case 82:
					return this.tokenTypes.LAMBDA_KEYWORD;
				case 141:
					break;
				case 3:
					return this.tokenTypes.IDENTIFIER;
				case 142:
					break;
				case 56:
					return this.tokenTypes.LTLT;
				case 143:
					break;
				case 47:
					return this.tokenTypes.FLOORDIV;
				case 144:
					break;
				case 71:
					return this.tokenTypes.FROM_KEYWORD;
				case 145:
					break;
				case 73:
					return this.tokenTypes.RAISE_KEYWORD;
				case 146:
					break;
				case 21:
					return this.tokenTypes.GT;
				case 147:
					break;
				case 39:
					return this.tokenTypes.IN_KEYWORD;
				case 148:
					break;
				case 51:
					return this.tokenTypes.XOREQ;
				case 149:
					break;
				case 69:
					return this.tokenTypes.ELIF_KEYWORD;
				case 150:
					break;
				case 18:
					return this.tokenTypes.AND;
				case 151:
					break;
				case 62:
					return this.tokenTypes.TRY_KEYWORD;
				case 152:
					break;
				case 50:
					return this.tokenTypes.OREQ;
				case 153:
					break;
				case 19:
					return this.tokenTypes.OR;
				case 154:
					break;
				case 53:
					return this.tokenTypes.GTGT;
				case 155:
					break;
				case 46:
					return this.tokenTypes.DIVEQ;
				case 156:
					break;
				case 61:
					return this.tokenTypes.DEF_KEYWORD;
				case 157:
					break;
				case 86:
					return this.tokenTypes.CONTINUE_KEYWORD;
				case 158:
					break;
				case 36:
					return this.tokenTypes.IMAGINARY_LITERAL;
				case 159:
					break;
				case 30:
					return this.tokenTypes.AT;
				case 160:
					break;
				case 78:
					return this.tokenTypes.WHILE_KEYWORD;
				case 161:
					break;
				case 49:
					return this.tokenTypes.ANDEQ;
				case 162:
					break;
				case 57:
					return this.tokenTypes.NE;
				case 163:
					break;
				case 42:
					return this.tokenTypes.PLUSEQ;
				case 164:
					break;
				case 81:
					return this.tokenTypes.RETURN_KEYWORD;
				case 165:
					break;
				case 83:
					return this.tokenTypes.IMPORT_KEYWORD;
				case 166:
					break;
				case 60:
					return this.tokenTypes.DEL_KEYWORD;
				case 167:
					break;
				case 11:
					return this.tokenTypes.TAB;
				case 168:
					break;
				case 48:
					return this.tokenTypes.PERCEQ;
				case 169:
					break;
				case 64:
					return this.tokenTypes.EXPEQ;
				case 170:
					break;
				case 54:
					return this.tokenTypes.LE;
				case 171:
					break;
				case 4:
					return this.tokenTypes.END_OF_LINE_COMMENT;
				case 172:
					break;
				default:
					if ((zzInput == -1) && (this.zzStartRead == this.zzCurrentPos)) {
						this.zzAtEOF = true;
						zzDoEOF();
						return null;
					}

					zzScanError(1);
			}
		}
	}
}