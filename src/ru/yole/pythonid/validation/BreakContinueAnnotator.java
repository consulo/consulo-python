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

package ru.yole.pythonid.validation;

import com.intellij.psi.PsiElement;
import ru.yole.pythonid.psi.PyBreakStatement;
import ru.yole.pythonid.psi.PyContinueStatement;
import ru.yole.pythonid.psi.PyTryFinallyStatement;

public class BreakContinueAnnotator extends PyAnnotator {
	@Override
	public void visitPyBreakStatement(PyBreakStatement node) {
		if (node.getContainingElement(node.getLanguage().getElementTypes().LOOPS) == null)
			getHolder().createErrorAnnotation(node, "'break' outside of loop");
	}

	@Override
	public void visitPyContinueStatement(PyContinueStatement node) {
		if (node.getContainingElement(node.getLanguage().getElementTypes().LOOPS) == null) {
			getHolder().createErrorAnnotation(node, "'continue' outside of loop");
			return;
		}
		PyTryFinallyStatement tryStatement = (PyTryFinallyStatement) node.getContainingElement(PyTryFinallyStatement.class);
		if (tryStatement != null) {
			PsiElement parent = node.getParent();
			while (parent != null) {
				if (parent == tryStatement.getFinallyStatementList()) {
					getHolder().createErrorAnnotation(node, "'continue' not allowed inside 'finally' clause");
					break;
				}
				parent = parent.getParent();
			}
		}
	}
}