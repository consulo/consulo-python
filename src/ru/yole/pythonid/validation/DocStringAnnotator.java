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

import com.intellij.lang.annotation.Annotation;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.psi.PsiElement;
import ru.yole.pythonid.psi.*;
import ru.yole.pythonid.psi.impl.PyFileImpl;

public class DocStringAnnotator extends PyAnnotator {
	@Override
	public void visitPyExpressionStatement(PyExpressionStatement node) {
		PsiElement parent = node.getParent();
		if (((parent instanceof PyFileImpl)) && (parent.getChildren()[0] == node)) {
			annotateDocStringStmt(node);
		} else if (((parent instanceof PyStatementList)) && (parent.getChildren()[0] == node)) {
			PsiElement stmtParent = parent.getParent();
			if (((stmtParent instanceof PyFunction)) || ((stmtParent instanceof PyClass)))
				annotateDocStringStmt(node);
		}
	}

	private void annotateDocStringStmt(PyExpressionStatement stmt) {
		PyExpression expr = stmt.getExpression();
		if ((expr instanceof PyLiteralExpression)) {
			PyLiteralExpression litExpr = (PyLiteralExpression) expr;
			PsiElement elt = litExpr.getFirstChild();
			if ((elt != null) && (elt.getNode().getElementType() == stmt.getLanguage().getTokenTypes().STRING_LITERAL)) {
				Annotation ann = getHolder().createInfoAnnotation(elt, null);
				ann.setTextAttributes(HighlighterColors.JAVA_DOC_COMMENT);
			}
		}
	}
}