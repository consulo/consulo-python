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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PyUtil {

	public static void ensureWritable(PsiElement element) {
		PsiDocumentManager docmgr = PsiDocumentManager.getInstance(element.getProject());

		PsiFile containingFile = element.getContainingFile();
		if (containingFile == null) {
			return;
		}
		Document doc = docmgr.getDocument(containingFile);
		if (doc == null) {
			return;
		}
		if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(doc, element.getProject())) {
			throw new IllegalStateException();
		}
	}

	public static PsiElement addLineToExpression(PyExpression expr, PyExpression add, boolean forceAdd, Comparator<String> comparator) {
		return addLineToExpressionIfMissing(expr, add, null, forceAdd, comparator);
	}

	@Nullable
	public static PsiElement addLineToExpressionIfMissing(PyExpression expr, PyExpression add, String line, boolean forceAdd, Comparator<String> comparator) {
		Project project = expr.getProject();
		if ((expr instanceof PyListLiteralExpression)) {
			PyListLiteralExpression listExp = (PyListLiteralExpression) expr;
			if ((comparator != null) && ((add instanceof PyStringLiteralExpression))) {
				PyStringLiteralExpression addLiteral = (PyStringLiteralExpression) add;

				List<PyStringLiteralExpression> literals = new ArrayList<PyStringLiteralExpression>();

				for (PyExpression exp : listExp.getElements()) {
					if ((exp instanceof PyStringLiteralExpression)) {
						PyStringLiteralExpression str = (PyStringLiteralExpression) exp;
						if ((line != null) && (str.getStringValue().equals(line))) {
							return null;
						}
						literals.add(str);
					}
				}

				String addval = addLiteral.getStringValue();
				PyStringLiteralExpression after = null;
				boolean quitnext = false;
				for (PyStringLiteralExpression literal : literals) {
					String val = literal.getStringValue();
					if (val != null) {
						if (comparator.compare(val, addval) < 0) {
							after = literal;
							quitnext = true;
						} else {
							if (quitnext) break;
						}
					}
				}
				try {
					if (after == null) {
						if (literals.size() == 0) {
							return listExp.add(add);
						}
						return listExp.addBefore(add, literals.get(0));
					}

					return listExp.addAfter(add, after);
				} catch (IncorrectOperationException e) {
					throw new RuntimeException(e);
				}
			}
			try {
				return listExp.add(add);
			} catch (IncorrectOperationException e) {
				throw new RuntimeException(e);
			}
		}

		if ((expr instanceof PyBinaryExpression)) {
			PyBinaryExpression binexp = (PyBinaryExpression) expr;
			if (binexp.isOperator("+")) {
				PsiElement b = addLineToExpressionIfMissing(binexp.getLeftExpression(), add, line, false, comparator);

				if (b != null) {
					return b;
				}
				PsiElement c = addLineToExpressionIfMissing(binexp.getRightExpression(), add, line, false, comparator);

				if (c != null) {
					return c;
				}
			}
		}
		if (forceAdd) {
			PyListLiteralExpression listLiteral = PyElementGenerator.getInstance().createListLiteral(project);
			PsiElement added;
			try {
				added = listLiteral.add(add);
			} catch (IncorrectOperationException e) {
				throw new RuntimeException(e);
			}
			PyBinaryExpression binExpr = PyElementGenerator.getInstance().createBinaryExpression(project, "+", expr, listLiteral);

			ASTNode exprNode = expr.getNode();
			ASTNode parent = exprNode.getTreeParent();
			parent.replaceChild(exprNode, binExpr.getNode());
			return added;
		}

		return null;
	}

	public static ASTNode getNextNonWhitespace(ASTNode after) {
		ASTNode node = after;
		do
			node = node.getTreeNext();
		while (isWhitespace(node));
		return node;
	}

	public static ASTNode getPrevNonWhitespace(ASTNode after) {
		ASTNode node = after;
		do
			node = node.getTreePrev();
		while (isWhitespace(node));
		return node;
	}

	private static boolean isWhitespace(ASTNode node) {
		return (node != null) && (node.getElementType().equals(TokenType.WHITE_SPACE));
	}
}