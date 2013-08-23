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

package ru.yole.pythonid.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyExpressionStatement;

public class PyExpressionStatementImpl extends PyElementImpl
		implements PyExpressionStatement {
	public PyExpressionStatementImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	@NotNull
	public PyExpression getExpression() {
		PyExpression tmp15_12 = ((PyExpression) childToPsiNotNull(getPyElementTypes().EXPRESSIONS, 0));
		if (tmp15_12 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp15_12;
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyExpressionStatement(this);
	}
}