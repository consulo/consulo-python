// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.psi.PyElementType;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;

public interface PyElementTypes {
  IElementType FUNCTION_DECLARATION = PyElementTypesFacade.getInstance().getFunctionDeclaration();
  IElementType CLASS_DECLARATION = PyElementTypesFacade.getInstance().getClassDeclaration();
  IElementType PARAMETER_LIST = PyElementTypesFacade.getInstance().getParameterList();
  IElementType DECORATOR_LIST = PyElementTypesFacade.getInstance().getDecoratorList();
  IElementType NAMED_PARAMETER = PyElementTypesFacade.getInstance().getNamedParameter();
  IElementType TUPLE_PARAMETER = PyElementTypesFacade.getInstance().getTupleParameter();
  IElementType SLASH_PARAMETER = PyElementTypesFacade.getInstance().getSlashParameter();
  IElementType SINGLE_STAR_PARAMETER = PyElementTypesFacade.getInstance().getSingleStarParameter();
  IElementType DECORATOR_CALL = PyElementTypesFacade.getInstance().getDecoratorCall();
  IElementType IMPORT_ELEMENT = PyElementTypesFacade.getInstance().getImportElement();
  IElementType ANNOTATION = PyElementTypesFacade.getInstance().getAnnotation();
  IElementType STAR_IMPORT_ELEMENT = PyElementTypesFacade.getInstance().getStarImportElement();
  IElementType EXCEPT_PART = PyElementTypesFacade.getInstance().getExceptPart();
  IElementType FROM_IMPORT_STATEMENT = PyElementTypesFacade.getInstance().getFromImportStatement();
  IElementType IMPORT_STATEMENT = PyElementTypesFacade.getInstance().getImportStatement();
  IElementType TARGET_EXPRESSION = PyElementTypesFacade.getInstance().getTargetExpression();
  IElementType TYPE_PARAMETER = PyElementTypesFacade.getInstance().getTypeParameter();
  IElementType TYPE_PARAMETER_LIST = PyElementTypesFacade.getInstance().getTypeParameterList();
  IElementType TYPE_ALIAS_STATEMENT = PyElementTypesFacade.getInstance().getTypeAliasStatement();
  IElementType STATEMENT_LIST = PyElementTypesFacade.getInstance().getStatementList();

  TokenSet PARAMETER_LIST_SET = TokenSet.create(PARAMETER_LIST);

  TokenSet FORMAL_PARAMETER_SET = TokenSet.create(NAMED_PARAMETER);

  PyElementType ARGUMENT_LIST = new PyElementType("ARGUMENT_LIST", PyElementTypesFacade.getInstance().getArgumentListConstructor());
  PyElementType PRINT_TARGET = new PyElementType("PRINT_TARGET", PyElementTypesFacade.getInstance().getPrintTargetConstructor());
  PyElementType DECORATOR = new PyElementType("DECORATOR", PyElementTypesFacade.getInstance().getDecoratorConstructor());

  // Statements
  PyElementType EXPRESSION_STATEMENT = new PyElementType("EXPRESSION_STATEMENT", PyElementTypesFacade.getInstance().getExpressionStatementConstructor());
  PyElementType ASSIGNMENT_STATEMENT = new PyElementType("ASSIGNMENT_STATEMENT", PyElementTypesFacade.getInstance().getAssignmentStatementConstructor());
  PyElementType AUG_ASSIGNMENT_STATEMENT = new PyElementType("AUG_ASSIGNMENT_STATEMENT", PyElementTypesFacade.getInstance().getAugAssignmentStatementConstructor());
  PyElementType ASSERT_STATEMENT = new PyElementType("ASSERT_STATEMENT", PyElementTypesFacade.getInstance().getAssertStatementConstructor());
  PyElementType BREAK_STATEMENT = new PyElementType("BREAK_STATEMENT", PyElementTypesFacade.getInstance().getBreakStatementConstructor());
  PyElementType CONTINUE_STATEMENT = new PyElementType("CONTINUE_STATEMENT", PyElementTypesFacade.getInstance().getContinueStatementConstructor());
  PyElementType DEL_STATEMENT = new PyElementType("DEL_STATEMENT", PyElementTypesFacade.getInstance().getDelStatementConstructor());
  PyElementType EXEC_STATEMENT = new PyElementType("EXEC_STATEMENT", PyElementTypesFacade.getInstance().getExecStatementConstructor());
  PyElementType FOR_STATEMENT = new PyElementType("FOR_STATEMENT", PyElementTypesFacade.getInstance().getForStatementConstructor());
  PyElementType TYPE_DECLARATION_STATEMENT = new PyElementType("TYPE_DECLARATION_STATEMENT", PyElementTypesFacade.getInstance().getTypeDeclarationStatementConstructor());

  PyElementType GLOBAL_STATEMENT = new PyElementType("GLOBAL_STATEMENT", PyElementTypesFacade.getInstance().getGlobalStatementConstructor());
  PyElementType IF_STATEMENT = new PyElementType("IF_STATEMENT", PyElementTypesFacade.getInstance().getIfStatementConstructor());
  PyElementType PASS_STATEMENT = new PyElementType("PASS_STATEMENT", PyElementTypesFacade.getInstance().getPassStatementConstructor());
  PyElementType PRINT_STATEMENT = new PyElementType("PRINT_STATEMENT", PyElementTypesFacade.getInstance().getPrintStatementConstructor());
  PyElementType RAISE_STATEMENT = new PyElementType("RAISE_STATEMENT", PyElementTypesFacade.getInstance().getRaiseStatementConstructor());
  PyElementType RETURN_STATEMENT = new PyElementType("RETURN_STATEMENT", PyElementTypesFacade.getInstance().getReturnStatementConstructor());
  PyElementType TRY_EXCEPT_STATEMENT = new PyElementType("TRY_EXCEPT_STATEMENT", PyElementTypesFacade.getInstance().getTryExceptStatementConstructor());
  PyElementType WITH_STATEMENT = new PyElementType("WITH_STATEMENT", PyElementTypesFacade.getInstance().getWithStatementConstructor());
  PyElementType WHILE_STATEMENT = new PyElementType("WHILE_STATEMENT", PyElementTypesFacade.getInstance().getWhileStatementConstructor());

  PyElementType NONLOCAL_STATEMENT = new PyElementType("NONLOCAL_STATEMENT", PyElementTypesFacade.getInstance().getNonlocalStatementConstructor());
  PyElementType WITH_ITEM = new PyElementType("WITH_ITEM", PyElementTypesFacade.getInstance().getWithItemConstructor());
  // Expressions
  PyElementType EMPTY_EXPRESSION = new PyElementType("EMPTY_EXPRESSION", PyElementTypesFacade.getInstance().getEmptyExpressionConstructor());
  PyElementType REFERENCE_EXPRESSION = new PyElementType("REFERENCE_EXPRESSION", PyElementTypesFacade.getInstance().getReferenceExpressionConstructor());

  PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementType("INTEGER_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getIntegerLiteralExpressionConstructor());
  PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementType("FLOAT_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getFloatLiteralExpressionConstructor());
  PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementType("IMAGINARY_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getImaginaryLiteralExpressionConstructor());
  PyElementType STRING_LITERAL_EXPRESSION = new PyElementType("STRING_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getStringLiteralExpressionConstructor());
  PyElementType NONE_LITERAL_EXPRESSION = new PyElementType("NONE_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getNoneLiteralExpressionConstructor());
  PyElementType ELLIPSIS_LITERAL_EXPRESSION = new PyElementType("ELLIPSIS_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getEllipsisLiteralExpressionConstructor());
  PyElementType BOOL_LITERAL_EXPRESSION = new PyElementType("BOOL_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getBoolLiteralExpressionConstructor());
  PyElementType PARENTHESIZED_EXPRESSION = new PyElementType("PARENTHESIZED_EXPRESSION", PyElementTypesFacade.getInstance().getParenthesizedExpressionConstructor());
  PyElementType SUBSCRIPTION_EXPRESSION = new PyElementType("SUBSCRIPTION_EXPRESSION", PyElementTypesFacade.getInstance().getSubscriptionExpressionConstructor());
  PyElementType SLICE_ITEM = new PyElementType("SLICE_ITEM", PyElementTypesFacade.getInstance().getSliceItemConstructor());
  PyElementType BINARY_EXPRESSION = new PyElementType("BINARY_EXPRESSION", PyElementTypesFacade.getInstance().getBinaryExpressionConstructor());
  PyElementType PREFIX_EXPRESSION = new PyElementType("PREFIX_EXPRESSION", PyElementTypesFacade.getInstance().getPrefixExpressionConstructor());
  PyElementType CALL_EXPRESSION = new PyElementType("CALL_EXPRESSION", PyElementTypesFacade.getInstance().getCallExpressionConstructor());
  PyElementType LIST_LITERAL_EXPRESSION = new PyElementType("LIST_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getListLiteralExpressionConstructor());
  PyElementType TUPLE_EXPRESSION = new PyElementType("TUPLE_EXPRESSION", PyElementTypesFacade.getInstance().getTupleExpressionConstructor());
  PyElementType KEYWORD_ARGUMENT_EXPRESSION = new PyElementType("KEYWORD_ARGUMENT_EXPRESSION", PyElementTypesFacade.getInstance().getKeywordArgumentExpressionConstructor());
  PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementType("STAR_ARGUMENT_EXPRESSION", PyElementTypesFacade.getInstance().getStarArgumentExpressionConstructor());
  PyElementType LAMBDA_EXPRESSION = new PyElementType("LAMBDA_EXPRESSION", PyElementTypesFacade.getInstance().getLambdaExpressionConstructor());
  PyElementType LIST_COMP_EXPRESSION = new PyElementType("LIST_COMP_EXPRESSION", PyElementTypesFacade.getInstance().getListCompExpressionConstructor());
  PyElementType DICT_LITERAL_EXPRESSION = new PyElementType("DICT_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getDictLiteralExpressionConstructor());
  PyElementType KEY_VALUE_EXPRESSION = new PyElementType("KEY_VALUE_EXPRESSION", PyElementTypesFacade.getInstance().getKeyValueExpressionConstructor());
  PyElementType REPR_EXPRESSION = new PyElementType("REPR_EXPRESSION", PyElementTypesFacade.getInstance().getReprExpressionConstructor());
  PyElementType GENERATOR_EXPRESSION = new PyElementType("GENERATOR_EXPRESSION", PyElementTypesFacade.getInstance().getGeneratorExpressionConstructor());
  PyElementType CONDITIONAL_EXPRESSION = new PyElementType("CONDITIONAL_EXPRESSION", PyElementTypesFacade.getInstance().getConditionalExpressionConstructor());
  PyElementType YIELD_EXPRESSION = new PyElementType("YIELD_EXPRESSION", PyElementTypesFacade.getInstance().getYieldExpressionConstructor());
  PyElementType STAR_EXPRESSION = new PyElementType("STAR_EXPRESSION", PyElementTypesFacade.getInstance().getStarExpressionConstructor());
  PyElementType DOUBLE_STAR_EXPRESSION = new PyElementType("DOUBLE_STAR_EXPRESSION", PyElementTypesFacade.getInstance().getDoubleStarExpressionConstructor());
  PyElementType ASSIGNMENT_EXPRESSION = new PyElementType("ASSIGNMENT_EXPRESSION", PyElementTypesFacade.getInstance().getAssignmentExpressionConstructor());

  PyElementType SET_LITERAL_EXPRESSION = new PyElementType("SET_LITERAL_EXPRESSION", PyElementTypesFacade.getInstance().getSetLiteralExpressionConstructor());
  PyElementType SET_COMP_EXPRESSION = new PyElementType("SET_COMP_EXPRESSION", PyElementTypesFacade.getInstance().getSetCompExpressionConstructor());
  PyElementType DICT_COMP_EXPRESSION = new PyElementType("DICT_COMP_EXPRESSION", PyElementTypesFacade.getInstance().getDictCompExpressionConstructor());
  TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);

  // Parts
  PyElementType IF_PART_IF = new PyElementType("IF_IF", PyElementTypesFacade.getInstance().getIfPartIfConstructor());
  PyElementType IF_PART_ELIF = new PyElementType("IF_ELIF", PyElementTypesFacade.getInstance().getIfPartElifConstructor());

  PyElementType FOR_PART = new PyElementType("FOR_PART", PyElementTypesFacade.getInstance().getForPartConstructor());
  PyElementType WHILE_PART = new PyElementType("WHILE_PART", PyElementTypesFacade.getInstance().getWhilePartConstructor());

  PyElementType TRY_PART = new PyElementType("TRY_PART", PyElementTypesFacade.getInstance().getTryPartConstructor());
  PyElementType FINALLY_PART = new PyElementType("FINALLY_PART", PyElementTypesFacade.getInstance().getFinallyPartConstructor());

  PyElementType ELSE_PART = new PyElementType("ELSE_PART", PyElementTypesFacade.getInstance().getElsePartConstructor());

  TokenSet PARTS = TokenSet.create(IF_PART_IF, IF_PART_ELIF, FOR_PART, WHILE_PART, TRY_PART, FINALLY_PART, ELSE_PART, EXCEPT_PART);
  TokenSet ELIFS = TokenSet.create(IF_PART_ELIF);
  TokenSet STAR_PARAMETERS = TokenSet.create(NAMED_PARAMETER, STAR_ARGUMENT_EXPRESSION, STAR_EXPRESSION, DOUBLE_STAR_EXPRESSION);
  TokenSet CLASS_OR_FUNCTION = TokenSet.create(CLASS_DECLARATION, FUNCTION_DECLARATION);
  TokenSet IMPORT_STATEMENTS = TokenSet.create(IMPORT_STATEMENT, FROM_IMPORT_STATEMENT);

  PyElementType FSTRING_NODE = new PyElementType("FSTRING_NODE", PyElementTypesFacade.getInstance().getFStringNodeConstructor());
  PyElementType FSTRING_FRAGMENT = new PyElementType("FSTRING_FRAGMENT", PyElementTypesFacade.getInstance().getFStringFragmentConstructor());
  PyElementType FSTRING_FRAGMENT_FORMAT_PART = new PyElementType("FSTRING_FRAGMENT_FORMAT_PART", PyElementTypesFacade.getInstance().getFStringFragmentFormatPartConstructor());

  PyElementType MATCH_STATEMENT = new PyElementType("MATCH_STATEMENT", PyElementTypesFacade.getInstance().getMatchStatementConstructor());
  PyElementType CASE_CLAUSE = new PyElementType("CASE_CLAUSE", PyElementTypesFacade.getInstance().getCaseClauseConstructor());
  PyElementType LITERAL_PATTERN = new PyElementType("LITERAL_PATTERN", PyElementTypesFacade.getInstance().getLiteralPatternConstructor());
  PyElementType VALUE_PATTERN = new PyElementType("VALUE_PATTERN", PyElementTypesFacade.getInstance().getValuePatternConstructor());
  PyElementType CAPTURE_PATTERN = new PyElementType("CAPTURE_PATTERN", PyElementTypesFacade.getInstance().getCapturePatternConstructor());
  PyElementType WILDCARD_PATTERN = new PyElementType("WILDCARD_PATTERN", PyElementTypesFacade.getInstance().getWildcardPatternConstructor());
  PyElementType GROUP_PATTERN = new PyElementType("GROUP_PATTERN", PyElementTypesFacade.getInstance().getGroupPatternConstructor());
  PyElementType SEQUENCE_PATTERN = new PyElementType("SEQUENCE_PATTERN", PyElementTypesFacade.getInstance().getSequencePatternConstructor());
  PyElementType SINGLE_STAR_PATTERN = new PyElementType("SINGLE_STAR_PATTERN", PyElementTypesFacade.getInstance().getSingleStarPatternConstructor());
  PyElementType DOUBLE_STAR_PATTERN = new PyElementType("DOUBLE_STAR_PATTERN", PyElementTypesFacade.getInstance().getDoubleStarPatternConstructor());
  PyElementType MAPPING_PATTERN = new PyElementType("KEY_VALUE_PATTERN", PyElementTypesFacade.getInstance().getMappingPatternConstructor());
  PyElementType KEY_VALUE_PATTERN = new PyElementType("KEY_VALUE_PATTERN", PyElementTypesFacade.getInstance().getKeyValuePatternConstructor());
  PyElementType CLASS_PATTERN = new PyElementType("CLASS_PATTERN", PyElementTypesFacade.getInstance().getClassPatternConstructor());
  PyElementType PATTERN_ARGUMENT_LIST = new PyElementType("PATTERN_ARGUMENT_LIST", PyElementTypesFacade.getInstance().getPatternArgumentListConstructor());
  PyElementType KEYWORD_PATTERN = new PyElementType("KEYWORD_PATTERN", PyElementTypesFacade.getInstance().getKeywordPatternConstructor());
  PyElementType OR_PATTERN = new PyElementType("OR_PATTERN", PyElementTypesFacade.getInstance().getOrPatternConstructor());
  PyElementType AS_PATTERN = new PyElementType("AS_PATTERN", PyElementTypesFacade.getInstance().getAsPatternConstructor());

}
