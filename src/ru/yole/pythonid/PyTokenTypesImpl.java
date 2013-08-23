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

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import ru.yole.pythonid.psi.impl.PyElementTypeImpl;

public class PyTokenTypesImpl extends PyTokenTypes
{
  public PyTokenTypesImpl(PythonLanguage language)
  {
    this.IDENTIFIER = new PyElementTypeImpl("IDENTIFIER", language);
    this.LINE_BREAK = new PyElementTypeImpl("LINE_BREAK", language);
    this.STATEMENT_BREAK = new PyElementTypeImpl("STATEMENT_BREAK", language);
    this.SPACE = new PyElementTypeImpl("SPACE", language);
    this.TAB = new PyElementTypeImpl("TAB", language);
    this.FORMFEED = new PyElementTypeImpl("FORMFEED", language);
    this.BAD_CHARACTER = TokenType.BAD_CHARACTER;
    this.INCONSISTENT_DEDENT = TokenType.BAD_CHARACTER;

    this.END_OF_LINE_COMMENT = new PyElementTypeImpl("END_OF_LINE_COMMENT", language);

    this.AND_KEYWORD = new PyElementTypeImpl("AND_KEYWORD", language);
    this.ASSERT_KEYWORD = new PyElementTypeImpl("ASSERT_KEYWORD", language);
    this.BREAK_KEYWORD = new PyElementTypeImpl("BREAK_KEYWORD", language);
    this.CLASS_KEYWORD = new PyElementTypeImpl("CLASS_KEYWORD", language);
    this.CONTINUE_KEYWORD = new PyElementTypeImpl("CONTINUE_KEYWORD", language);
    this.DEF_KEYWORD = new PyElementTypeImpl("DEF_KEYWORD", language);
    this.DEL_KEYWORD = new PyElementTypeImpl("DEL_KEYWORD", language);
    this.ELIF_KEYWORD = new PyElementTypeImpl("ELIF_KEYWORD", language);
    this.ELSE_KEYWORD = new PyElementTypeImpl("ELSE_KEYWORD", language);
    this.EXCEPT_KEYWORD = new PyElementTypeImpl("EXCEPT_KEYWORD", language);
    this.EXEC_KEYWORD = new PyElementTypeImpl("EXEC_KEYWORD", language);
    this.FINALLY_KEYWORD = new PyElementTypeImpl("FINALLY_KEYWORD", language);
    this.FOR_KEYWORD = new PyElementTypeImpl("FOR_KEYWORD", language);
    this.FROM_KEYWORD = new PyElementTypeImpl("FROM_KEYWORD", language);
    this.GLOBAL_KEYWORD = new PyElementTypeImpl("GLOBAL_KEYWORD", language);
    this.IF_KEYWORD = new PyElementTypeImpl("IF_KEYWORD", language);
    this.IMPORT_KEYWORD = new PyElementTypeImpl("IMPORT_KEYWORD", language);
    this.IN_KEYWORD = new PyElementTypeImpl("IN_KEYWORD", language);
    this.IS_KEYWORD = new PyElementTypeImpl("IS_KEYWORD", language);
    this.LAMBDA_KEYWORD = new PyElementTypeImpl("LAMBDA_KEYWORD", language);
    this.NOT_KEYWORD = new PyElementTypeImpl("NOT_KEYWORD", language);
    this.OR_KEYWORD = new PyElementTypeImpl("OR_KEYWORD", language);
    this.PASS_KEYWORD = new PyElementTypeImpl("PASS_KEYWORD", language);
    this.PRINT_KEYWORD = new PyElementTypeImpl("PRINT_KEYWORD", language);
    this.RAISE_KEYWORD = new PyElementTypeImpl("RAISE_KEYWORD", language);
    this.RETURN_KEYWORD = new PyElementTypeImpl("RETURN_KEYWORD", language);
    this.TRY_KEYWORD = new PyElementTypeImpl("TRY_KEYWORD", language);
    this.WHILE_KEYWORD = new PyElementTypeImpl("WHILE_KEYWORD", language);
    this.YIELD_KEYWORD = new PyElementTypeImpl("YIELD_KEYWORD", language);

    this.KEYWORDS = TokenSet.create(new IElementType[] { this.AND_KEYWORD, this.ASSERT_KEYWORD, this.BREAK_KEYWORD, this.CLASS_KEYWORD, this.CONTINUE_KEYWORD, this.DEF_KEYWORD, this.DEL_KEYWORD, this.ELIF_KEYWORD, this.ELSE_KEYWORD, this.EXCEPT_KEYWORD, this.EXEC_KEYWORD, this.FINALLY_KEYWORD, this.FOR_KEYWORD, this.FROM_KEYWORD, this.GLOBAL_KEYWORD, this.IF_KEYWORD, this.IMPORT_KEYWORD, this.IN_KEYWORD, this.IS_KEYWORD, this.LAMBDA_KEYWORD, this.NOT_KEYWORD, this.OR_KEYWORD, this.PASS_KEYWORD, this.PRINT_KEYWORD, this.RAISE_KEYWORD, this.RETURN_KEYWORD, this.TRY_KEYWORD, this.WHILE_KEYWORD, this.YIELD_KEYWORD });

    this.INTEGER_LITERAL = new PyElementTypeImpl("INTEGER_LITERAL", language);
    this.FLOAT_LITERAL = new PyElementTypeImpl("FLOAT_LITERAL", language);
    this.IMAGINARY_LITERAL = new PyElementTypeImpl("IMAGINARY_LITERAL", language);
    this.STRING_LITERAL = new PyElementTypeImpl("STRING_LITERAL", language);

    this.PLUS = new PyElementTypeImpl("PLUS", language);
    this.MINUS = new PyElementTypeImpl("MINUS", language);
    this.MULT = new PyElementTypeImpl("MULT", language);
    this.EXP = new PyElementTypeImpl("EXP", language);
    this.DIV = new PyElementTypeImpl("DIV", language);
    this.FLOORDIV = new PyElementTypeImpl("FLOORDIV", language);
    this.PERC = new PyElementTypeImpl("PERC", language);
    this.LTLT = new PyElementTypeImpl("LTLT", language);
    this.GTGT = new PyElementTypeImpl("GTGT", language);
    this.AND = new PyElementTypeImpl("AND", language);
    this.OR = new PyElementTypeImpl("OR", language);
    this.XOR = new PyElementTypeImpl("XOR", language);
    this.TILDE = new PyElementTypeImpl("TILDE", language);
    this.LT = new PyElementTypeImpl("LT", language);
    this.GT = new PyElementTypeImpl("GT", language);
    this.LE = new PyElementTypeImpl("LE", language);
    this.GE = new PyElementTypeImpl("GE", language);
    this.EQEQ = new PyElementTypeImpl("EQEQ", language);
    this.NE = new PyElementTypeImpl("NE", language);
    this.NE_OLD = new PyElementTypeImpl("NE_OLD", language);

    this.LPAR = new PyElementTypeImpl("LPAR", language);
    this.RPAR = new PyElementTypeImpl("RPAR", language);
    this.LBRACKET = new PyElementTypeImpl("LBRACKET", language);
    this.RBRACKET = new PyElementTypeImpl("RBRACKET", language);
    this.LBRACE = new PyElementTypeImpl("LBRACE", language);
    this.RBRACE = new PyElementTypeImpl("RBRACE", language);
    this.AT = new PyElementTypeImpl("AT", language);
    this.COMMA = new PyElementTypeImpl("COMMA", language);
    this.COLON = new PyElementTypeImpl("COLON", language);
    this.DOT = new PyElementTypeImpl("DOT", language);
    this.TICK = new PyElementTypeImpl("TICK", language);
    this.EQ = new PyElementTypeImpl("EQ", language);
    this.SEMICOLON = new PyElementTypeImpl("SEMICOLON", language);
    this.PLUSEQ = new PyElementTypeImpl("PLUSEQ", language);
    this.MINUSEQ = new PyElementTypeImpl("MINUSEQ", language);
    this.MULTEQ = new PyElementTypeImpl("MULTEQ", language);
    this.DIVEQ = new PyElementTypeImpl("DIVEQ", language);
    this.FLOORDIVEQ = new PyElementTypeImpl("FLOORDIVEQ", language);
    this.PERCEQ = new PyElementTypeImpl("PERCEQ", language);
    this.ANDEQ = new PyElementTypeImpl("ANDEQ", language);
    this.OREQ = new PyElementTypeImpl("OREQ", language);
    this.XOREQ = new PyElementTypeImpl("XOREQ", language);
    this.LTLTEQ = new PyElementTypeImpl("LTLTEQ", language);
    this.GTGTEQ = new PyElementTypeImpl("GTGTEQ", language);
    this.EXPEQ = new PyElementTypeImpl("EXPEQ", language);

    this.OPERATIONS = TokenSet.create(new IElementType[] { this.PLUS, this.MINUS, this.MULT, this.EXP, this.DIV, this.FLOORDIV, this.PERC, this.LTLT, this.GTGT, this.AND, this.OR, this.XOR, this.TILDE, this.LT, this.GT, this.LE, this.GE, this.EQEQ, this.NE, this.NE_OLD, this.AT, this.COLON, this.TICK, this.EQ, this.PLUSEQ, this.MINUSEQ, this.MULTEQ, this.DIVEQ, this.FLOORDIVEQ, this.PERCEQ, this.ANDEQ, this.OREQ, this.XOREQ, this.LTLTEQ, this.GTGTEQ, this.EXPEQ });

    this.COMPARISON_OPERATIONS = TokenSet.create(new IElementType[] { this.LT, this.GT, this.EQEQ, this.GE, this.LE, this.NE, this.NE_OLD, this.IN_KEYWORD, this.IS_KEYWORD, this.NOT_KEYWORD });

    this.SHIFT_OPERATIONS = TokenSet.create(new IElementType[] { this.LTLT, this.GTGT });
    this.ADDITIVE_OPERATIONS = TokenSet.create(new IElementType[] { this.PLUS, this.MINUS });
    this.MULTIPLICATIVE_OPERATIONS = TokenSet.create(new IElementType[] { this.MULT, this.FLOORDIV, this.DIV, this.PERC });
    this.UNARY_OPERATIONS = TokenSet.create(new IElementType[] { this.PLUS, this.MINUS, this.TILDE });
    this.END_OF_STATEMENT = TokenSet.create(new IElementType[] { this.STATEMENT_BREAK, this.SEMICOLON });
    this.WHITESPACE = TokenSet.create(new IElementType[] { this.SPACE, this.TAB, this.FORMFEED });
    this.WHITESPACE_OR_LINEBREAK = TokenSet.create(new IElementType[] { this.SPACE, this.TAB, this.FORMFEED, this.LINE_BREAK });
    this.OPEN_BRACES = TokenSet.create(new IElementType[] { this.LBRACKET, this.LBRACE, this.LPAR });
    this.CLOSE_BRACES = TokenSet.create(new IElementType[] { this.RBRACKET, this.RBRACE, this.RPAR });

    this.AUG_ASSIGN_OPERATIONS = TokenSet.create(new IElementType[] { this.PLUSEQ, this.MINUSEQ, this.MULTEQ, this.DIVEQ, this.PERCEQ, this.EXPEQ, this.GTGTEQ, this.LTLTEQ, this.ANDEQ, this.OREQ, this.XOREQ });

    this.BACKSLASH = new PyElementTypeImpl("BACKSLASH", language);

    this.INDENT = new PyElementTypeImpl("INDENT", language);
    this.DEDENT = new PyElementTypeImpl("DEDENT", language);
  }
}