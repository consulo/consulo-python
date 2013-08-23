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

package org.consulo.python.psi.impl;

import com.intellij.lang.ASTNode;
import org.consulo.python.PyElementTypes;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExceptBlock;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyExceptBlockImpl extends PyElementImpl
		implements PyExceptBlock {
	public PyExceptBlockImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyExceptBlock(this);
	}

	@Override
	@Nullable
	public PyExpression getExceptClass() {
		return childToPsi(PyElementTypes.EXPRESSIONS, 0);
	}

	@Override
	@Nullable
	public PyExpression getTarget() {
		return childToPsi(PyElementTypes.EXPRESSIONS, 1);
	}

	@Override
	@NotNull
	public PyStatementList getStatementList() {
		PyStatementList tmp14_11 = childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
		return tmp14_11;
	}
}