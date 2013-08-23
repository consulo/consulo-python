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

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.consulo.python.psi.PyElementType;
import org.consulo.python.psi.impl.PyElementTypeImpl;

public interface PyTokenTypes {
	PyElementType IDENTIFIER = new PyElementTypeImpl("IDENTIFIER");
	PyElementType LINE_BREAK = new PyElementTypeImpl("LINE_BREAK");
	PyElementType STATEMENT_BREAK = new PyElementTypeImpl("STATEMENT_BREAK");
	PyElementType SPACE = new PyElementTypeImpl("SPACE");
	PyElementType TAB = new PyElementTypeImpl("TAB");
	PyElementType FORMFEED = new PyElementTypeImpl("FORMFEED");
	IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
	IElementType INCONSISTENT_DEDENT = TokenType.BAD_CHARACTER;

	PyElementType END_OF_LINE_COMMENT = new PyElementTypeImpl("END_OF_LINE_COMMENT");

	PyElementType AND_KEYWORD = new PyElementTypeImpl("AND_KEYWORD");
	PyElementType ASSERT_KEYWORD = new PyElementTypeImpl("ASSERT_KEYWORD");
	PyElementType BREAK_KEYWORD = new PyElementTypeImpl("BREAK_KEYWORD");
	PyElementType CLASS_KEYWORD = new PyElementTypeImpl("CLASS_KEYWORD");
	PyElementType CONTINUE_KEYWORD = new PyElementTypeImpl("CONTINUE_KEYWORD");
	PyElementType DEF_KEYWORD = new PyElementTypeImpl("DEF_KEYWORD");
	PyElementType DEL_KEYWORD = new PyElementTypeImpl("DEL_KEYWORD");
	PyElementType ELIF_KEYWORD = new PyElementTypeImpl("ELIF_KEYWORD");
	PyElementType ELSE_KEYWORD = new PyElementTypeImpl("ELSE_KEYWORD");
	PyElementType EXCEPT_KEYWORD = new PyElementTypeImpl("EXCEPT_KEYWORD");
	PyElementType EXEC_KEYWORD = new PyElementTypeImpl("EXEC_KEYWORD");
	PyElementType FINALLY_KEYWORD = new PyElementTypeImpl("FINALLY_KEYWORD");
	PyElementType FOR_KEYWORD = new PyElementTypeImpl("FOR_KEYWORD");
	PyElementType FROM_KEYWORD = new PyElementTypeImpl("FROM_KEYWORD");
	PyElementType GLOBAL_KEYWORD = new PyElementTypeImpl("GLOBAL_KEYWORD");
	PyElementType IF_KEYWORD = new PyElementTypeImpl("IF_KEYWORD");
	PyElementType IMPORT_KEYWORD = new PyElementTypeImpl("IMPORT_KEYWORD");
	PyElementType IN_KEYWORD = new PyElementTypeImpl("IN_KEYWORD");
	PyElementType IS_KEYWORD = new PyElementTypeImpl("IS_KEYWORD");
	PyElementType LAMBDA_KEYWORD = new PyElementTypeImpl("LAMBDA_KEYWORD");
	PyElementType NOT_KEYWORD = new PyElementTypeImpl("NOT_KEYWORD");
	PyElementType OR_KEYWORD = new PyElementTypeImpl("OR_KEYWORD");
	PyElementType PASS_KEYWORD = new PyElementTypeImpl("PASS_KEYWORD");
	PyElementType PRINT_KEYWORD = new PyElementTypeImpl("PRINT_KEYWORD");
	PyElementType RAISE_KEYWORD = new PyElementTypeImpl("RAISE_KEYWORD");
	PyElementType RETURN_KEYWORD = new PyElementTypeImpl("RETURN_KEYWORD");
	PyElementType TRY_KEYWORD = new PyElementTypeImpl("TRY_KEYWORD");
	PyElementType WHILE_KEYWORD = new PyElementTypeImpl("WHILE_KEYWORD");
	PyElementType YIELD_KEYWORD = new PyElementTypeImpl("YIELD_KEYWORD");


	PyElementType INTEGER_LITERAL = new PyElementTypeImpl("INTEGER_LITERAL");
	PyElementType FLOAT_LITERAL = new PyElementTypeImpl("FLOAT_LITERAL");
	PyElementType IMAGINARY_LITERAL = new PyElementTypeImpl("IMAGINARY_LITERAL");
	PyElementType STRING_LITERAL = new PyElementTypeImpl("STRING_LITERAL");

	PyElementType PLUS = new PyElementTypeImpl("PLUS");
	PyElementType MINUS = new PyElementTypeImpl("MINUS");
	PyElementType MULT = new PyElementTypeImpl("MULT");
	PyElementType EXP = new PyElementTypeImpl("EXP");
	PyElementType DIV = new PyElementTypeImpl("DIV");
	PyElementType FLOORDIV = new PyElementTypeImpl("FLOORDIV");
	PyElementType PERC = new PyElementTypeImpl("PERC");
	PyElementType LTLT = new PyElementTypeImpl("LTLT");
	PyElementType GTGT = new PyElementTypeImpl("GTGT");
	PyElementType AND = new PyElementTypeImpl("AND");
	PyElementType OR = new PyElementTypeImpl("OR");
	PyElementType XOR = new PyElementTypeImpl("XOR");
	PyElementType TILDE = new PyElementTypeImpl("TILDE");
	PyElementType LT = new PyElementTypeImpl("LT");
	PyElementType GT = new PyElementTypeImpl("GT");
	PyElementType LE = new PyElementTypeImpl("LE");
	PyElementType GE = new PyElementTypeImpl("GE");
	PyElementType EQEQ = new PyElementTypeImpl("EQEQ");
	PyElementType NE = new PyElementTypeImpl("NE");
	PyElementType NE_OLD = new PyElementTypeImpl("NE_OLD");

	PyElementType LPAR = new PyElementTypeImpl("LPAR");
	PyElementType RPAR = new PyElementTypeImpl("RPAR");
	PyElementType LBRACKET = new PyElementTypeImpl("LBRACKET");
	PyElementType RBRACKET = new PyElementTypeImpl("RBRACKET");
	PyElementType LBRACE = new PyElementTypeImpl("LBRACE");
	PyElementType RBRACE = new PyElementTypeImpl("RBRACE");
	PyElementType AT = new PyElementTypeImpl("AT");
	PyElementType COMMA = new PyElementTypeImpl("COMMA");
	PyElementType COLON = new PyElementTypeImpl("COLON");
	PyElementType DOT = new PyElementTypeImpl("DOT");
	PyElementType TICK = new PyElementTypeImpl("TICK");
	PyElementType EQ = new PyElementTypeImpl("EQ");
	PyElementType SEMICOLON = new PyElementTypeImpl("SEMICOLON");
	PyElementType PLUSEQ = new PyElementTypeImpl("PLUSEQ");
	PyElementType MINUSEQ = new PyElementTypeImpl("MINUSEQ");
	PyElementType MULTEQ = new PyElementTypeImpl("MULTEQ");
	PyElementType DIVEQ = new PyElementTypeImpl("DIVEQ");
	PyElementType FLOORDIVEQ = new PyElementTypeImpl("FLOORDIVEQ");
	PyElementType PERCEQ = new PyElementTypeImpl("PERCEQ");
	PyElementType ANDEQ = new PyElementTypeImpl("ANDEQ");
	PyElementType OREQ = new PyElementTypeImpl("OREQ");
	PyElementType XOREQ = new PyElementTypeImpl("XOREQ");
	PyElementType LTLTEQ = new PyElementTypeImpl("LTLTEQ");
	PyElementType GTGTEQ = new PyElementTypeImpl("GTGTEQ");
	PyElementType EXPEQ = new PyElementTypeImpl("EXPEQ");

	PyElementType BACKSLASH = new PyElementTypeImpl("BACKSLASH");

	PyElementType INDENT = new PyElementTypeImpl("INDENT");
	PyElementType DEDENT = new PyElementTypeImpl("DEDENT");
}