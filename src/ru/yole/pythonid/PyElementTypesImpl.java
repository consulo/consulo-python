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
import ru.yole.pythonid.psi.impl.PyArgumentListImpl;
import ru.yole.pythonid.psi.impl.PyAssertStatementImpl;
import ru.yole.pythonid.psi.impl.PyAssignmentStatementImpl;
import ru.yole.pythonid.psi.impl.PyAugAssignmentStatementImpl;
import ru.yole.pythonid.psi.impl.PyBinaryExpressionImpl;
import ru.yole.pythonid.psi.impl.PyBreakStatementImpl;
import ru.yole.pythonid.psi.impl.PyCallExpressionImpl;
import ru.yole.pythonid.psi.impl.PyClassImpl;
import ru.yole.pythonid.psi.impl.PyContinueStatementImpl;
import ru.yole.pythonid.psi.impl.PyDecoratedFunctionImpl;
import ru.yole.pythonid.psi.impl.PyDelStatementImpl;
import ru.yole.pythonid.psi.impl.PyDictLiteralExpressionImpl;
import ru.yole.pythonid.psi.impl.PyElementTypeImpl;
import ru.yole.pythonid.psi.impl.PyEmptyExpressionImpl;
import ru.yole.pythonid.psi.impl.PyExceptBlockImpl;
import ru.yole.pythonid.psi.impl.PyExecStatementImpl;
import ru.yole.pythonid.psi.impl.PyExpressionStatementImpl;
import ru.yole.pythonid.psi.impl.PyForStatementImpl;
import ru.yole.pythonid.psi.impl.PyFromImportStatementImpl;
import ru.yole.pythonid.psi.impl.PyFunctionImpl;
import ru.yole.pythonid.psi.impl.PyGeneratorExpressionImpl;
import ru.yole.pythonid.psi.impl.PyGlobalStatementImpl;
import ru.yole.pythonid.psi.impl.PyIfStatementImpl;
import ru.yole.pythonid.psi.impl.PyImportElementImpl;
import ru.yole.pythonid.psi.impl.PyImportStatementImpl;
import ru.yole.pythonid.psi.impl.PyKeyValueExpressionImpl;
import ru.yole.pythonid.psi.impl.PyKeywordArgumentImpl;
import ru.yole.pythonid.psi.impl.PyLambdaExpressionImpl;
import ru.yole.pythonid.psi.impl.PyListCompExpressionImpl;
import ru.yole.pythonid.psi.impl.PyListLiteralExpressionImpl;
import ru.yole.pythonid.psi.impl.PyNumericLiteralExpressionImpl;
import ru.yole.pythonid.psi.impl.PyParameterImpl;
import ru.yole.pythonid.psi.impl.PyParameterListImpl;
import ru.yole.pythonid.psi.impl.PyParenthesizedExpressionImpl;
import ru.yole.pythonid.psi.impl.PyPassStatementImpl;
import ru.yole.pythonid.psi.impl.PyPrefixExpressionImpl;
import ru.yole.pythonid.psi.impl.PyPrintStatementImpl;
import ru.yole.pythonid.psi.impl.PyPrintTargetImpl;
import ru.yole.pythonid.psi.impl.PyRaiseStatementImpl;
import ru.yole.pythonid.psi.impl.PyReferenceExpressionImpl;
import ru.yole.pythonid.psi.impl.PyReprExpressionImpl;
import ru.yole.pythonid.psi.impl.PyReturnStatementImpl;
import ru.yole.pythonid.psi.impl.PySliceExpressionImpl;
import ru.yole.pythonid.psi.impl.PyStarArgumentImpl;
import ru.yole.pythonid.psi.impl.PyStatementListImpl;
import ru.yole.pythonid.psi.impl.PyStringLiteralExpressionImpl;
import ru.yole.pythonid.psi.impl.PySubscriptionExpressionImpl;
import ru.yole.pythonid.psi.impl.PyTargetExpressionImpl;
import ru.yole.pythonid.psi.impl.PyTryExceptStatementImpl;
import ru.yole.pythonid.psi.impl.PyTryFinallyStatementImpl;
import ru.yole.pythonid.psi.impl.PyTupleExpressionImpl;
import ru.yole.pythonid.psi.impl.PyWhileStatementImpl;
import ru.yole.pythonid.psi.impl.PyYieldStatementImpl;

public class PyElementTypesImpl extends PyElementTypes
{
  public PyElementTypesImpl(PythonLanguage language)
  {
    this.FUNCTION_DECLARATION = new PyElementTypeImpl("FUNCTION_DECLARATION", PyFunctionImpl.class, language);

    this.DECORATED_FUNCTION_DECLARATION = new PyElementTypeImpl("DECORATED_FUNCTION_DECLARATION", PyDecoratedFunctionImpl.class, language);

    this.PARAMETER_LIST = new PyElementTypeImpl("PARAMETER_LIST", PyParameterListImpl.class, language);

    this.FORMAL_PARAMETER = new PyElementTypeImpl("FORMAL_PARAMETER", PyParameterImpl.class, language);

    this.ARGUMENT_LIST = new PyElementTypeImpl("ARGUMENT_LIST", PyArgumentListImpl.class, language);

    this.IMPORT_ELEMENT = new PyElementTypeImpl("IMPORT_ELEMENT", PyImportElementImpl.class, language);

    this.EXCEPT_BLOCK = new PyElementTypeImpl("EXCEPT_BLOCK", PyExceptBlockImpl.class, language);

    this.CLASS_DECLARATION = new PyElementTypeImpl("CLASS_DECLARATION", PyClassImpl.class, language);

    this.PRINT_TARGET = new PyElementTypeImpl("PRINT_TARGET", PyPrintTargetImpl.class, language);

    this.EXPRESSION_STATEMENT = new PyElementTypeImpl("EXPRESSION_STATEMENT", PyExpressionStatementImpl.class, language);

    this.ASSIGNMENT_STATEMENT = new PyElementTypeImpl("ASSIGNMENT_STATEMENT", PyAssignmentStatementImpl.class, language);

    this.AUG_ASSIGNMENT_STATEMENT = new PyElementTypeImpl("AUG_ASSIGNMENT_STATEMENT", PyAugAssignmentStatementImpl.class, language);

    this.ASSERT_STATEMENT = new PyElementTypeImpl("ASSERT_STATEMENT", PyAssertStatementImpl.class, language);

    this.BREAK_STATEMENT = new PyElementTypeImpl("BREAK_STATEMENT", PyBreakStatementImpl.class, language);

    this.CONTINUE_STATEMENT = new PyElementTypeImpl("CONTINUE_STATEMENT", PyContinueStatementImpl.class, language);

    this.DEL_STATEMENT = new PyElementTypeImpl("DEL_STATEMENT", PyDelStatementImpl.class, language);

    this.EXEC_STATEMENT = new PyElementTypeImpl("EXEC_STATEMENT", PyExecStatementImpl.class, language);

    this.FOR_STATEMENT = new PyElementTypeImpl("FOR_STATEMENT", PyForStatementImpl.class, language);

    this.FROM_IMPORT_STATEMENT = new PyElementTypeImpl("FROM_IMPORT_STATEMENT", PyFromImportStatementImpl.class, language);

    this.GLOBAL_STATEMENT = new PyElementTypeImpl("GLOBAL_STATEMENT", PyGlobalStatementImpl.class, language);

    this.IMPORT_STATEMENT = new PyElementTypeImpl("IMPORT_STATEMENT", PyImportStatementImpl.class, language);

    this.IF_STATEMENT = new PyElementTypeImpl("IF_STATEMENT", PyIfStatementImpl.class, language);

    this.PASS_STATEMENT = new PyElementTypeImpl("PASS_STATEMENT", PyPassStatementImpl.class, language);

    this.PRINT_STATEMENT = new PyElementTypeImpl("PRINT_STATEMENT", PyPrintStatementImpl.class, language);

    this.RAISE_STATEMENT = new PyElementTypeImpl("RAISE_STATEMENT", PyRaiseStatementImpl.class, language);

    this.RETURN_STATEMENT = new PyElementTypeImpl("RETURN_STATEMENT", PyReturnStatementImpl.class, language);

    this.TRY_EXCEPT_STATEMENT = new PyElementTypeImpl("TRY_EXCEPT_STATEMENT", PyTryExceptStatementImpl.class, language);

    this.TRY_FINALLY_STATEMENT = new PyElementTypeImpl("TRY_FINALLY_STATEMENT", PyTryFinallyStatementImpl.class, language);

    this.WHILE_STATEMENT = new PyElementTypeImpl("WHILE_STATEMENT", PyWhileStatementImpl.class, language);

    this.YIELD_STATEMENT = new PyElementTypeImpl("YIELD_STATEMENT", PyYieldStatementImpl.class, language);

    this.STATEMENT_LIST = new PyElementTypeImpl("STATEMENT_LIST", PyStatementListImpl.class, language);

    this.STATEMENTS = TokenSet.create(new IElementType[] { this.EXPRESSION_STATEMENT, this.ASSIGNMENT_STATEMENT, this.AUG_ASSIGNMENT_STATEMENT, this.ASSERT_STATEMENT, this.BREAK_STATEMENT, this.CONTINUE_STATEMENT, this.DEL_STATEMENT, this.EXEC_STATEMENT, this.FOR_STATEMENT, this.FROM_IMPORT_STATEMENT, this.GLOBAL_STATEMENT, this.IMPORT_STATEMENT, this.IF_STATEMENT, this.PASS_STATEMENT, this.PRINT_STATEMENT, this.RAISE_STATEMENT, this.RETURN_STATEMENT, this.TRY_EXCEPT_STATEMENT, this.TRY_FINALLY_STATEMENT, this.WHILE_STATEMENT, this.YIELD_STATEMENT });

    this.LOOPS = TokenSet.create(new IElementType[] { this.WHILE_STATEMENT, this.FOR_STATEMENT });

    this.EMPTY_EXPRESSION = new PyElementTypeImpl("EMPTY_EXPRESSION", PyEmptyExpressionImpl.class, language);

    this.REFERENCE_EXPRESSION = new PyElementTypeImpl("REFERENCE_EXPRESSION", PyReferenceExpressionImpl.class, language);

    this.TARGET_EXPRESSION = new PyElementTypeImpl("TARGET_EXPRESSION", PyTargetExpressionImpl.class, language);

    this.INTEGER_LITERAL_EXPRESSION = new PyElementTypeImpl("INTEGER_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class, language);

    this.FLOAT_LITERAL_EXPRESSION = new PyElementTypeImpl("FLOAT_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class, language);

    this.IMAGINARY_LITERAL_EXPRESSION = new PyElementTypeImpl("IMAGINARY_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class, language);

    this.STRING_LITERAL_EXPRESSION = new PyElementTypeImpl("STRING_LITERAL_EXPRESSION", PyStringLiteralExpressionImpl.class, language);

    this.PARENTHESIZED_EXPRESSION = new PyElementTypeImpl("PARENTHESIZED_EXPRESSION", PyParenthesizedExpressionImpl.class, language);

    this.SUBSCRIPTION_EXPRESSION = new PyElementTypeImpl("SUBSCRIPTION_EXPRESSION", PySubscriptionExpressionImpl.class, language);

    this.SLICE_EXPRESSION = new PyElementTypeImpl("SLICE_EXPRESSION", PySliceExpressionImpl.class, language);

    this.BINARY_EXPRESSION = new PyElementTypeImpl("BINARY_EXPRESSION", PyBinaryExpressionImpl.class, language);

    this.PREFIX_EXPRESSION = new PyElementTypeImpl("PREFIX_EXPRESSION", PyPrefixExpressionImpl.class, language);

    this.CALL_EXPRESSION = new PyElementTypeImpl("CALL_EXPRESSION", PyCallExpressionImpl.class, language);

    this.LIST_LITERAL_EXPRESSION = new PyElementTypeImpl("LIST_LITERAL_EXPRESSION", PyListLiteralExpressionImpl.class, language);

    this.TUPLE_EXPRESSION = new PyElementTypeImpl("TUPLE_EXPRESSION", PyTupleExpressionImpl.class, language);

    this.KEYWORD_ARGUMENT_EXPRESSION = new PyElementTypeImpl("KEYWORD_ARGUMENT_EXPRESSION", PyKeywordArgumentImpl.class, language);

    this.STAR_ARGUMENT_EXPRESSION = new PyElementTypeImpl("STAR_ARGUMENT_EXPRESSION", PyStarArgumentImpl.class, language);

    this.LAMBDA_EXPRESSION = new PyElementTypeImpl("LAMBDA_EXPRESSION", PyLambdaExpressionImpl.class, language);

    this.LIST_COMP_EXPRESSION = new PyElementTypeImpl("LIST_COMP_EXPRESSION", PyListCompExpressionImpl.class, language);

    this.DICT_LITERAL_EXPRESSION = new PyElementTypeImpl("DICT_LITERAL_EXPRESSION", PyDictLiteralExpressionImpl.class, language);

    this.KEY_VALUE_EXPRESSION = new PyElementTypeImpl("KEY_VALUE_EXPRESSION", PyKeyValueExpressionImpl.class, language);

    this.REPR_EXPRESSION = new PyElementTypeImpl("REPR_EXPRESSION", PyReprExpressionImpl.class, language);

    this.GENERATOR_EXPRESSION = new PyElementTypeImpl("GENERATOR_EXPRESSION", PyGeneratorExpressionImpl.class, language);

    this.EXPRESSIONS = TokenSet.create(new IElementType[] { this.EMPTY_EXPRESSION, this.REFERENCE_EXPRESSION, this.INTEGER_LITERAL_EXPRESSION, this.FLOAT_LITERAL_EXPRESSION, this.IMAGINARY_LITERAL_EXPRESSION, this.STRING_LITERAL_EXPRESSION, this.PARENTHESIZED_EXPRESSION, this.SUBSCRIPTION_EXPRESSION, this.SLICE_EXPRESSION, this.BINARY_EXPRESSION, this.PREFIX_EXPRESSION, this.CALL_EXPRESSION, this.LIST_LITERAL_EXPRESSION, this.TUPLE_EXPRESSION, this.KEYWORD_ARGUMENT_EXPRESSION, this.STAR_ARGUMENT_EXPRESSION, this.LAMBDA_EXPRESSION, this.LIST_COMP_EXPRESSION, this.DICT_LITERAL_EXPRESSION, this.KEY_VALUE_EXPRESSION, this.REPR_EXPRESSION, this.GENERATOR_EXPRESSION, this.TARGET_EXPRESSION });

    this.STATEMENT_LISTS = TokenSet.create(new IElementType[] { this.STATEMENT_LIST });
  }
}