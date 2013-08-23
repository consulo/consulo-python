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

package org.consulo.python.psi;

import com.intellij.psi.PsiElementVisitor;

public class PyElementVisitor extends PsiElementVisitor {
	public void visitPyElement(PyElement node) {
		visitElement(node);
	}

	public void visitPyReferenceExpression(PyReferenceExpression node) {
		visitPyExpression(node);
	}

	public void visitPyTargetExpression(PyTargetExpression node) {
		visitPyExpression(node);
	}

	public void visitPyCallExpression(PyCallExpression node) {
		visitPyExpression(node);
	}

	public void visitPyGeneratorExpression(PyGeneratorExpression node) {
		visitPyExpression(node);
	}

	public void visitPyBinaryExpression(PyBinaryExpression node) {
		visitPyExpression(node);
	}

	public void visitPyTupleExpression(PyTupleExpression node) {
		visitPyExpression(node);
	}

	public void visitPyParenthesizedExpression(PyParenthesizedExpression node) {
		visitPyExpression(node);
	}

	public void visitPyListLiteralExpression(PyListLiteralExpression node) {
		visitPyExpression(node);
	}

	public void visitPyListCompExpression(PyListCompExpression node) {
		visitPyExpression(node);
	}

	public void visitPyLambdaExpression(PyLambdaExpression node) {
		visitPyExpression(node);
	}

	public void visitPyAssignmentStatement(PyAssignmentStatement node) {
		visitPyStatement(node);
	}

	public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
		visitPyStatement(node);
	}

	public void visitPyDelStatement(PyDelStatement node) {
		visitPyStatement(node);
	}

	public void visitPyReturnStatement(PyReturnStatement node) {
		visitPyStatement(node);
	}

	public void visitPyYieldStatement(PyYieldStatement node) {
		visitPyStatement(node);
	}

	public void visitPyTryExceptStatement(PyTryExceptStatement node) {
		visitPyStatement(node);
	}

	public void visitPyTryFinallyStatement(PyTryFinallyStatement node) {
		visitPyStatement(node);
	}

	public void visitPyBreakStatement(PyBreakStatement node) {
		visitPyStatement(node);
	}

	public void visitPyContinueStatement(PyContinueStatement node) {
		visitPyStatement(node);
	}

	public void visitPyGlobalStatement(PyGlobalStatement node) {
		visitPyStatement(node);
	}

	public void visitPyFromImportStatement(PyFromImportStatement node) {
		visitPyStatement(node);
	}

	public void visitPyIfStatement(PyIfStatement node) {
		visitPyStatement(node);
	}

	public void visitPyForStatement(PyForStatement node) {
		visitPyStatement(node);
	}

	public void visitPyWhileStatement(PyWhileStatement node) {
		visitPyStatement(node);
	}

	public void visitPyExpressionStatement(PyExpressionStatement node) {
		visitPyStatement(node);
	}

	public void visitPyStatement(PyStatement node) {
		visitPyElement(node);
	}

	public void visitPyExpression(PyExpression node) {
		visitPyElement(node);
	}

	public void visitPyParameterList(PyParameterList node) {
		visitPyElement(node);
	}

	public void visitPyParameter(PyParameter node) {
		visitPyElement(node);
	}

	public void visitPyArgumentList(PyArgumentList node) {
		visitPyElement(node);
	}

	public void visitPyStatementList(PyStatementList node) {
		visitPyElement(node);
	}

	public void visitPyExceptBlock(PyExceptBlock node) {
		visitPyElement(node);
	}

	public void visitPyFunction(PyFunction node) {
		visitPyElement(node);
	}

	public void visitPyClass(PyClass node) {
		visitPyElement(node);
	}

	public void visitPyFile(PyFile node) {
		visitPyElement(node);
	}

	public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
		visitPyElement(node);
	}

	public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
		visitPyElement(node);
	}
}