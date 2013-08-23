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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import ru.yole.pythonid.psi.PyElementType;

public class PyTokenTypes {
	public PyElementType IDENTIFIER;
	public PyElementType LINE_BREAK;
	public PyElementType STATEMENT_BREAK;
	public PyElementType SPACE;
	public PyElementType TAB;
	public PyElementType FORMFEED;
	public IElementType BAD_CHARACTER;
	public IElementType INCONSISTENT_DEDENT;
	public PyElementType END_OF_LINE_COMMENT;
	public PyElementType AND_KEYWORD;
	public PyElementType ASSERT_KEYWORD;
	public PyElementType BREAK_KEYWORD;
	public PyElementType CLASS_KEYWORD;
	public PyElementType CONTINUE_KEYWORD;
	public PyElementType DEF_KEYWORD;
	public PyElementType DEL_KEYWORD;
	public PyElementType ELIF_KEYWORD;
	public PyElementType ELSE_KEYWORD;
	public PyElementType EXCEPT_KEYWORD;
	public PyElementType EXEC_KEYWORD;
	public PyElementType FINALLY_KEYWORD;
	public PyElementType FOR_KEYWORD;
	public PyElementType FROM_KEYWORD;
	public PyElementType GLOBAL_KEYWORD;
	public PyElementType IF_KEYWORD;
	public PyElementType IMPORT_KEYWORD;
	public PyElementType IN_KEYWORD;
	public PyElementType IS_KEYWORD;
	public PyElementType LAMBDA_KEYWORD;
	public PyElementType NOT_KEYWORD;
	public PyElementType OR_KEYWORD;
	public PyElementType PASS_KEYWORD;
	public PyElementType PRINT_KEYWORD;
	public PyElementType RAISE_KEYWORD;
	public PyElementType RETURN_KEYWORD;
	public PyElementType TRY_KEYWORD;
	public PyElementType WHILE_KEYWORD;
	public PyElementType YIELD_KEYWORD;
	public TokenSet KEYWORDS;
	public PyElementType INTEGER_LITERAL;
	public PyElementType FLOAT_LITERAL;
	public PyElementType IMAGINARY_LITERAL;
	public PyElementType STRING_LITERAL;
	public PyElementType PLUS;
	public PyElementType MINUS;
	public PyElementType MULT;
	public PyElementType EXP;
	public PyElementType DIV;
	public PyElementType FLOORDIV;
	public PyElementType PERC;
	public PyElementType LTLT;
	public PyElementType GTGT;
	public PyElementType AND;
	public PyElementType OR;
	public PyElementType XOR;
	public PyElementType TILDE;
	public PyElementType LT;
	public PyElementType GT;
	public PyElementType LE;
	public PyElementType GE;
	public PyElementType EQEQ;
	public PyElementType NE;
	public PyElementType NE_OLD;
	public PyElementType LPAR;
	public PyElementType RPAR;
	public PyElementType LBRACKET;
	public PyElementType RBRACKET;
	public PyElementType LBRACE;
	public PyElementType RBRACE;
	public PyElementType AT;
	public PyElementType COMMA;
	public PyElementType COLON;
	public PyElementType DOT;
	public PyElementType TICK;
	public PyElementType EQ;
	public PyElementType SEMICOLON;
	public PyElementType PLUSEQ;
	public PyElementType MINUSEQ;
	public PyElementType MULTEQ;
	public PyElementType DIVEQ;
	public PyElementType FLOORDIVEQ;
	public PyElementType PERCEQ;
	public PyElementType ANDEQ;
	public PyElementType OREQ;
	public PyElementType XOREQ;
	public PyElementType LTLTEQ;
	public PyElementType GTGTEQ;
	public PyElementType EXPEQ;
	public TokenSet OPERATIONS;
	public TokenSet COMPARISON_OPERATIONS;
	public TokenSet SHIFT_OPERATIONS;
	public TokenSet ADDITIVE_OPERATIONS;
	public TokenSet MULTIPLICATIVE_OPERATIONS;
	public TokenSet UNARY_OPERATIONS;
	public TokenSet END_OF_STATEMENT;
	public TokenSet WHITESPACE;
	public TokenSet WHITESPACE_OR_LINEBREAK;
	public TokenSet OPEN_BRACES;
	public TokenSet CLOSE_BRACES;
	public TokenSet AUG_ASSIGN_OPERATIONS;
	public PyElementType BACKSLASH;
	public PyElementType INDENT;
	public PyElementType DEDENT;
}