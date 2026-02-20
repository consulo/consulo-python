/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import consulo.language.psi.PsiElementVisitor;

/**
 * Visitor for python-specific nodes.
 */
public class PyElementVisitor extends PsiElementVisitor
{
	public void visitPyElement(PyElement node)
	{
		visitElement(node);
	}

	public void visitPyReferenceExpression(PyReferenceExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyTargetExpression(PyTargetExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyCallExpression(PyCallExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyDecoratorList(PyDecoratorList node)
	{
		visitElement(node);
	}

	public void visitPyComprehensionElement(PyComprehensionElement node)
	{
		visitPyExpression(node);
	}

	public void visitPyGeneratorExpression(PyGeneratorExpression node)
	{
		visitPyComprehensionElement(node);
	}

	public void visitPyBinaryExpression(PyBinaryExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyPrefixExpression(PyPrefixExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPySequenceExpression(PySequenceExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyTupleExpression(PyTupleExpression node)
	{
		visitPySequenceExpression(node);
	}

	public void visitPyParenthesizedExpression(PyParenthesizedExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyDictLiteralExpression(PyDictLiteralExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyListLiteralExpression(PyListLiteralExpression node)
	{
		visitPySequenceExpression(node);
	}

	public void visitPySetLiteralExpression(PySetLiteralExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyListCompExpression(PyListCompExpression node)
	{
		visitPyComprehensionElement(node);
	}

	public void visitPyDictCompExpression(PyDictCompExpression node)
	{
		visitPyComprehensionElement(node);
	}

	public void visitPySetCompExpression(PySetCompExpression node)
	{
		visitPyComprehensionElement(node);
	}

	public void visitPyLambdaExpression(PyLambdaExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyAssignmentStatement(PyAssignmentStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyDelStatement(PyDelStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyReturnStatement(PyReturnStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyYieldExpression(PyYieldExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyTryExceptStatement(PyTryExceptStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyRaiseStatement(PyRaiseStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyBreakStatement(PyBreakStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyContinueStatement(PyContinueStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyGlobalStatement(PyGlobalStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyFromImportStatement(PyFromImportStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyIfStatement(PyIfStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyForStatement(PyForStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyWhileStatement(PyWhileStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyWithStatement(PyWithStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyExpressionStatement(PyExpressionStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyStatement(PyStatement node)
	{
		visitPyElement(node);
	}

	public void visitPyExpression(PyExpression node)
	{
		visitPyElement(node);
	}

	public void visitPyParameterList(PyParameterList node)
	{
		visitPyElement(node);
	}

	public void visitPyParameter(PyParameter node)
	{
		visitPyElement(node);
	}

	public void visitPyNamedParameter(PyNamedParameter node)
	{
		visitPyParameter(node);
	}

	public void visitPyTupleParameter(PyTupleParameter node)
	{
		visitPyParameter(node);
	}

	public void visitPyArgumentList(PyArgumentList node)
	{
		visitPyElement(node);
	}

	public void visitPyStatementList(PyStatementList node)
	{
		visitPyElement(node);
	}

	public void visitPyExceptBlock(PyExceptPart node)
	{
		visitPyElement(node);
	}

	public void visitPyFunction(PyFunction node)
	{
		visitPyElement(node);
	}

	public void visitPyClass(PyClass node)
	{
		visitPyElement(node);
	}

	public void visitPyFile(PyFile node)
	{
		visitPyElement(node);
	}

	public void visitPyStringLiteralExpression(PyStringLiteralExpression node)
	{
		visitPyElement(node);
	}

	public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node)
	{
		visitPyElement(node);
	}

	public void visitPyPrintStatement(PyPrintStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyImportStatement(PyImportStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyReprExpression(PyReprExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyNonlocalStatement(PyNonlocalStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyStarExpression(PyStarExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyDoubleStarExpression(PyDoubleStarExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPySubscriptionExpression(PySubscriptionExpression node)
	{
		visitPyExpression(node);
	}

	public void visitPyImportElement(PyImportElement node)
	{
		visitPyElement(node);
	}

	public void visitPyStarImportElement(PyStarImportElement node)
	{
		visitPyElement(node);
	}

	public void visitPyConditionalStatementPart(PyConditionalStatementPart node)
	{
		visitPyElement(node);
	}

	public void visitPyAssertStatement(PyAssertStatement node)
	{
		visitPyElement(node);
	}

	public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node)
	{
		visitPyElement(node);
	}

	public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node)
	{
		visitPyElement(node);
	}

	public void visitPyConditionalExpression(PyConditionalExpression node)
	{
		visitPyElement(node);
	}

	public void visitPyKeywordArgument(PyKeywordArgument node)
	{
		visitPyElement(node);
	}

	public void visitPyWithItem(PyWithItem node)
	{
		visitPyElement(node);
	}

	public void visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node)
	{
		visitPyStatement(node);
	}

	public void visitPyAnnotation(PyAnnotation node)
	{
		visitPyElement(node);
	}
}
