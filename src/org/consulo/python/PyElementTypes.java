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

import com.intellij.psi.tree.TokenSet;
import org.consulo.python.psi.PyElementType;
import org.consulo.python.psi.impl.*;

public interface PyElementTypes {
	PyElementType FUNCTION_DECLARATION = new PyElementTypeImpl("FUNCTION_DECLARATION", PyFunctionImpl.class);

	PyElementType DECORATED_FUNCTION_DECLARATION = new PyElementTypeImpl("DECORATED_FUNCTION_DECLARATION", PyDecoratedFunctionImpl.class);

	PyElementType PARAMETER_LIST = new PyElementTypeImpl("PARAMETER_LIST", PyParameterListImpl.class);

	PyElementType FORMAL_PARAMETER = new PyElementTypeImpl("FORMAL_PARAMETER", PyParameterImpl.class);

	PyElementType ARGUMENT_LIST = new PyElementTypeImpl("ARGUMENT_LIST", PyArgumentListImpl.class);

	PyElementType IMPORT_ELEMENT = new PyElementTypeImpl("IMPORT_ELEMENT", PyImportElementImpl.class);

	PyElementType EXCEPT_BLOCK = new PyElementTypeImpl("EXCEPT_BLOCK", PyExceptBlockImpl.class);

	PyElementType CLASS_DECLARATION = new PyElementTypeImpl("CLASS_DECLARATION", PyClassImpl.class);

	PyElementType PRINT_TARGET = new PyElementTypeImpl("PRINT_TARGET", PyPrintTargetImpl.class);

	PyElementType EXPRESSION_STATEMENT = new PyElementTypeImpl("EXPRESSION_STATEMENT", PyExpressionStatementImpl.class);

	PyElementType ASSIGNMENT_STATEMENT = new PyElementTypeImpl("ASSIGNMENT_STATEMENT", PyAssignmentStatementImpl.class);

	PyElementType AUG_ASSIGNMENT_STATEMENT = new PyElementTypeImpl("AUG_ASSIGNMENT_STATEMENT", PyAugAssignmentStatementImpl.class);

	PyElementType ASSERT_STATEMENT = new PyElementTypeImpl("ASSERT_STATEMENT", PyAssertStatementImpl.class);

	PyElementType BREAK_STATEMENT = new PyElementTypeImpl("BREAK_STATEMENT", PyBreakStatementImpl.class);

	PyElementType CONTINUE_STATEMENT = new PyElementTypeImpl("CONTINUE_STATEMENT", PyContinueStatementImpl.class);

	PyElementType DEL_STATEMENT = new PyElementTypeImpl("DEL_STATEMENT", PyDelStatementImpl.class);

	PyElementType EXEC_STATEMENT = new PyElementTypeImpl("EXEC_STATEMENT", PyExecStatementImpl.class);

	PyElementType FOR_STATEMENT = new PyElementTypeImpl("FOR_STATEMENT", PyForStatementImpl.class);

	PyElementType FROM_IMPORT_STATEMENT = new PyElementTypeImpl("FROM_IMPORT_STATEMENT", PyFromImportStatementImpl.class);

	PyElementType GLOBAL_STATEMENT = new PyElementTypeImpl("GLOBAL_STATEMENT", PyGlobalStatementImpl.class);

	PyElementType IMPORT_STATEMENT = new PyElementTypeImpl("IMPORT_STATEMENT", PyImportStatementImpl.class);

	PyElementType IF_STATEMENT = new PyElementTypeImpl("IF_STATEMENT", PyIfStatementImpl.class);

	PyElementType PASS_STATEMENT = new PyElementTypeImpl("PASS_STATEMENT", PyPassStatementImpl.class);

	PyElementType PRINT_STATEMENT = new PyElementTypeImpl("PRINT_STATEMENT", PyPrintStatementImpl.class);

	PyElementType RAISE_STATEMENT = new PyElementTypeImpl("RAISE_STATEMENT", PyRaiseStatementImpl.class);

	PyElementType RETURN_STATEMENT = new PyElementTypeImpl("RETURN_STATEMENT", PyReturnStatementImpl.class);

	PyElementType TRY_EXCEPT_STATEMENT = new PyElementTypeImpl("TRY_EXCEPT_STATEMENT", PyTryExceptStatementImpl.class);

	PyElementType TRY_FINALLY_STATEMENT = new PyElementTypeImpl("TRY_FINALLY_STATEMENT", PyTryFinallyStatementImpl.class);

	PyElementType WHILE_STATEMENT = new PyElementTypeImpl("WHILE_STATEMENT", PyWhileStatementImpl.class);

	PyElementType YIELD_STATEMENT = new PyElementTypeImpl("YIELD_STATEMENT", PyYieldStatementImpl.class);

	PyElementType STATEMENT_LIST = new PyElementTypeImpl("STATEMENT_LIST", PyStatementListImpl.class);

	TokenSet STATEMENTS = TokenSet.create(EXPRESSION_STATEMENT, ASSIGNMENT_STATEMENT, AUG_ASSIGNMENT_STATEMENT, ASSERT_STATEMENT,
			BREAK_STATEMENT, CONTINUE_STATEMENT, DEL_STATEMENT, EXEC_STATEMENT, FOR_STATEMENT, FROM_IMPORT_STATEMENT, GLOBAL_STATEMENT, IMPORT_STATEMENT, IF_STATEMENT, PASS_STATEMENT, PRINT_STATEMENT, RAISE_STATEMENT, RETURN_STATEMENT, TRY_EXCEPT_STATEMENT, TRY_FINALLY_STATEMENT, WHILE_STATEMENT, YIELD_STATEMENT);

	TokenSet LOOPS = TokenSet.create(WHILE_STATEMENT, FOR_STATEMENT);

	PyElementType EMPTY_EXPRESSION = new PyElementTypeImpl("EMPTY_EXPRESSION", PyEmptyExpressionImpl.class);

	PyElementType REFERENCE_EXPRESSION = new PyElementTypeImpl("REFERENCE_EXPRESSION", PyReferenceExpressionImpl.class);

	PyElementType TARGET_EXPRESSION = new PyElementTypeImpl("TARGET_EXPRESSION", PyTargetExpressionImpl.class);

	PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementTypeImpl("INTEGER_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);

	PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementTypeImpl("FLOAT_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);

	PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementTypeImpl("IMAGINARY_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);

	PyElementType STRING_LITERAL_EXPRESSION = new PyElementTypeImpl("STRING_LITERAL_EXPRESSION", PyStringLiteralExpressionImpl.class);

	PyElementType PARENTHESIZED_EXPRESSION = new PyElementTypeImpl("PARENTHESIZED_EXPRESSION", PyParenthesizedExpressionImpl.class);

	PyElementType SUBSCRIPTION_EXPRESSION = new PyElementTypeImpl("SUBSCRIPTION_EXPRESSION", PySubscriptionExpressionImpl.class);

	PyElementType SLICE_EXPRESSION = new PyElementTypeImpl("SLICE_EXPRESSION", PySliceExpressionImpl.class);

	PyElementType BINARY_EXPRESSION = new PyElementTypeImpl("BINARY_EXPRESSION", PyBinaryExpressionImpl.class);

	PyElementType PREFIX_EXPRESSION = new PyElementTypeImpl("PREFIX_EXPRESSION", PyPrefixExpressionImpl.class);

	PyElementType CALL_EXPRESSION = new PyElementTypeImpl("CALL_EXPRESSION", PyCallExpressionImpl.class);

	PyElementType LIST_LITERAL_EXPRESSION = new PyElementTypeImpl("LIST_LITERAL_EXPRESSION", PyListLiteralExpressionImpl.class);

	PyElementType TUPLE_EXPRESSION = new PyElementTypeImpl("TUPLE_EXPRESSION", PyTupleExpressionImpl.class);

	PyElementType KEYWORD_ARGUMENT_EXPRESSION = new PyElementTypeImpl("KEYWORD_ARGUMENT_EXPRESSION", PyKeywordArgumentImpl.class);

	PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementTypeImpl("STAR_ARGUMENT_EXPRESSION", PyStarArgumentImpl.class);

	PyElementType LAMBDA_EXPRESSION = new PyElementTypeImpl("LAMBDA_EXPRESSION", PyLambdaExpressionImpl.class);

	PyElementType LIST_COMP_EXPRESSION = new PyElementTypeImpl("LIST_COMP_EXPRESSION", PyListCompExpressionImpl.class);

	PyElementType DICT_LITERAL_EXPRESSION = new PyElementTypeImpl("DICT_LITERAL_EXPRESSION", PyDictLiteralExpressionImpl.class);

	PyElementType KEY_VALUE_EXPRESSION = new PyElementTypeImpl("KEY_VALUE_EXPRESSION", PyKeyValueExpressionImpl.class);

	PyElementType REPR_EXPRESSION = new PyElementTypeImpl("REPR_EXPRESSION", PyReprExpressionImpl.class);

	PyElementType GENERATOR_EXPRESSION = new PyElementTypeImpl("GENERATOR_EXPRESSION", PyGeneratorExpressionImpl.class);

	TokenSet EXPRESSIONS = TokenSet.create(EMPTY_EXPRESSION, REFERENCE_EXPRESSION, INTEGER_LITERAL_EXPRESSION,
			FLOAT_LITERAL_EXPRESSION, IMAGINARY_LITERAL_EXPRESSION, STRING_LITERAL_EXPRESSION, PARENTHESIZED_EXPRESSION, SUBSCRIPTION_EXPRESSION, SLICE_EXPRESSION, BINARY_EXPRESSION, PREFIX_EXPRESSION, CALL_EXPRESSION, LIST_LITERAL_EXPRESSION, TUPLE_EXPRESSION, KEYWORD_ARGUMENT_EXPRESSION, STAR_ARGUMENT_EXPRESSION, LAMBDA_EXPRESSION, LIST_COMP_EXPRESSION, DICT_LITERAL_EXPRESSION, KEY_VALUE_EXPRESSION, REPR_EXPRESSION, GENERATOR_EXPRESSION, TARGET_EXPRESSION);

	TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);
}