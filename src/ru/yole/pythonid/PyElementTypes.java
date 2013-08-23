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

import com.intellij.psi.tree.TokenSet;
import ru.yole.pythonid.psi.PyElementType;

public class PyElementTypes {
	public PyElementType FUNCTION_DECLARATION;
	public PyElementType DECORATED_FUNCTION_DECLARATION;
	public PyElementType PARAMETER_LIST;
	public PyElementType FORMAL_PARAMETER;
	public PyElementType ARGUMENT_LIST;
	public PyElementType IMPORT_ELEMENT;
	public PyElementType EXCEPT_BLOCK;
	public PyElementType CLASS_DECLARATION;
	public PyElementType PRINT_TARGET;
	public PyElementType EXPRESSION_STATEMENT;
	public PyElementType ASSIGNMENT_STATEMENT;
	public PyElementType AUG_ASSIGNMENT_STATEMENT;
	public PyElementType ASSERT_STATEMENT;
	public PyElementType BREAK_STATEMENT;
	public PyElementType CONTINUE_STATEMENT;
	public PyElementType DEL_STATEMENT;
	public PyElementType EXEC_STATEMENT;
	public PyElementType FOR_STATEMENT;
	public PyElementType FROM_IMPORT_STATEMENT;
	public PyElementType GLOBAL_STATEMENT;
	public PyElementType IMPORT_STATEMENT;
	public PyElementType IF_STATEMENT;
	public PyElementType PASS_STATEMENT;
	public PyElementType PRINT_STATEMENT;
	public PyElementType RAISE_STATEMENT;
	public PyElementType RETURN_STATEMENT;
	public PyElementType TRY_EXCEPT_STATEMENT;
	public PyElementType TRY_FINALLY_STATEMENT;
	public PyElementType WHILE_STATEMENT;
	public PyElementType YIELD_STATEMENT;
	public PyElementType STATEMENT_LIST;
	public TokenSet STATEMENTS;
	public TokenSet LOOPS;
	public PyElementType EMPTY_EXPRESSION;
	public PyElementType REFERENCE_EXPRESSION;
	public PyElementType TARGET_EXPRESSION;
	public PyElementType INTEGER_LITERAL_EXPRESSION;
	public PyElementType FLOAT_LITERAL_EXPRESSION;
	public PyElementType IMAGINARY_LITERAL_EXPRESSION;
	public PyElementType STRING_LITERAL_EXPRESSION;
	public PyElementType PARENTHESIZED_EXPRESSION;
	public PyElementType SUBSCRIPTION_EXPRESSION;
	public PyElementType SLICE_EXPRESSION;
	public PyElementType BINARY_EXPRESSION;
	public PyElementType PREFIX_EXPRESSION;
	public PyElementType CALL_EXPRESSION;
	public PyElementType LIST_LITERAL_EXPRESSION;
	public PyElementType TUPLE_EXPRESSION;
	public PyElementType KEYWORD_ARGUMENT_EXPRESSION;
	public PyElementType STAR_ARGUMENT_EXPRESSION;
	public PyElementType LAMBDA_EXPRESSION;
	public PyElementType LIST_COMP_EXPRESSION;
	public PyElementType DICT_LITERAL_EXPRESSION;
	public PyElementType KEY_VALUE_EXPRESSION;
	public PyElementType REPR_EXPRESSION;
	public PyElementType GENERATOR_EXPRESSION;
	public TokenSet EXPRESSIONS;
	public TokenSet STATEMENT_LISTS;
}