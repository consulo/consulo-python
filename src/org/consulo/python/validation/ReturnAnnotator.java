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

package org.consulo.python.validation;

import org.consulo.python.psi.*;

public class ReturnAnnotator extends PyAnnotator {
	@Override
	public void visitPyReturnStatement(PyReturnStatement node) {
		PyFunction function = node.getContainingElement(PyFunction.class);
		if (function == null) {
			getHolder().createErrorAnnotation(node, "'return' outside of function");
			return;
		}
		if (node.getExpression() != null) {
			YieldVisitor visitor = new YieldVisitor();
			function.acceptChildren(visitor);
			if (visitor.haveYield())
				getHolder().createErrorAnnotation(node, "'return' with argument inside generator");
		}
	}

	@Override
	public void visitPyYieldStatement(PyYieldStatement node) {
		PyFunction function = node.getContainingElement(PyFunction.class);
		if (function == null) {
			getHolder().createErrorAnnotation(node, "'yield' outside of function");
		}
		if (node.getContainingElement(PyTryFinallyStatement.class) != null)
			getHolder().createErrorAnnotation(node, "'yield' not allowed in a 'try' block with a 'finally' clause");
	}

	private class YieldVisitor extends PyElementVisitor {
		private boolean _haveYield = false;

		private YieldVisitor() {
		}

		public boolean haveYield() {
			return this._haveYield;
		}

		@Override
		public void visitPyYieldStatement(PyYieldStatement node) {
			this._haveYield = true;
		}

		@Override
		public void visitPyElement(PyElement node) {
			if (!this._haveYield)
				node.acceptChildren(this);
		}
	}
}