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
import org.consulo.python.psi.PyAugAssignmentStatement;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExpression;

public class PyAugAssignmentStatementImpl extends PyElementImpl
		implements PyAugAssignmentStatement {
	public PyAugAssignmentStatementImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyAugAssignmentStatement(this);
	}

	@Override
	public PyExpression getTarget() {
		PyExpression target = childToPsi(PyElementTypes.EXPRESSIONS, 0);
		if (target == null) {
			throw new RuntimeException("Target missing in augmented assignment statement");
		}
		return target;
	}
}