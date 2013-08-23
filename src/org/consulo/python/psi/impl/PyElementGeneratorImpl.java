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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.PythonFileType;
import org.consulo.python.psi.*;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Formatter;

public class PyElementGeneratorImpl extends PyElementGenerator {

	@Override
	public ASTNode createNameIdentifier(Project project, String name) {
		PsiFile dummyFile = createDummyFile(project, name);
		PyExpressionStatement expressionStatement = (PyExpressionStatement) dummyFile.getFirstChild();

		PyReferenceExpression refExpression = (PyReferenceExpression) expressionStatement.getFirstChild();

		return refExpression.getNode().getFirstChildNode();
	}

	private PsiFile createDummyFile(Project project, String contents) {
		return PsiFileFactory.getInstance(project).createFileFromText("dummy.py", PythonFileType.INSTANCE, contents);
	}

	@Override
	public PyStringLiteralExpression createStringLiteralAlreadyEscaped(Project project, String str) {
		PsiFile dummyFile = createDummyFile(project, str);
		PyExpressionStatement expressionStatement = (PyExpressionStatement) dummyFile.getFirstChild();

		return (PyStringLiteralExpression) expressionStatement.getFirstChild();
	}

	@Override
	public PyStringLiteralExpression createStringLiteralFromString(Project project, @Nullable PsiFile destination, String unescaped) {
		boolean useDouble = !unescaped.contains("\"");
		boolean useMulti = unescaped.matches(".*(\r|\n).*");
		String quotes;
		if (useMulti)
			quotes = useDouble ? "\"\"\"" : "'''";
		else {
			quotes = useDouble ? "\"" : "'";
		}
		StringBuilder buf = new StringBuilder(unescaped.length() * 2);
		buf.append(quotes);
		VirtualFile vfile = destination == null ? null : destination.getVirtualFile();
		Charset charset;
		if (vfile == null)
			charset = Charset.forName("US-ASCII");
		else {
			charset = vfile.getCharset();
		}
		CharsetEncoder encoder = charset.newEncoder();
		Formatter formatter = new Formatter(buf);
		boolean unicode = false;
		for (int i = 0; i < unescaped.length(); i++) {
			int c = unescaped.codePointAt(i);
			if ((c == 34) && (useDouble))
				buf.append("\\\"");
			else if ((c == 39) && (!useDouble))
				buf.append("\\'");
			else if (((c == 13) || (c == 10)) && (!useMulti)) {
				if (c == 13) buf.append("\\r");
				else if (c == 10) buf.append("\\n");
			} else if (!encoder.canEncode(new String(Character.toChars(c)))) {
				if (c <= 255) {
					formatter.format("\\x%02x", new Object[]{Integer.valueOf(c)});
				} else if (c < 65535) {
					unicode = true;
					formatter.format("\\u%04x", new Object[]{Integer.valueOf(c)});
				} else {
					unicode = true;
					formatter.format("\\U%08x", new Object[]{Integer.valueOf(c)});
				}
			} else buf.appendCodePoint(c);
		}

		buf.append(quotes);
		if (unicode) buf.insert(0, "u");

		return createStringLiteralAlreadyEscaped(project, buf.toString());
	}

	@Override
	public PyListLiteralExpression createListLiteral(Project project) {
		PsiFile dummyFile = createDummyFile(project, "[]");
		PyExpressionStatement expressionStatement = (PyExpressionStatement) dummyFile.getFirstChild();

		return (PyListLiteralExpression) expressionStatement.getFirstChild();
	}

	@Override
	public PyKeywordArgument createKeywordArgument(Project project, String keyword, @Nullable PyExpression expression) {
		PsiFile dummyFile = createDummyFile(project, "xyz(" + keyword + " = 0)");

		PyExpressionStatement expressionStatement = (PyExpressionStatement) dummyFile.getFirstChild();

		PyCallExpression call = (PyCallExpression) expressionStatement.getFirstChild();

		PyKeywordArgument keywordArg = (PyKeywordArgument) call.getArgumentList().getArguments()[0];

		ASTNode valNode = keywordArg.getValueExpression().getNode();
		ASTNode valParent = valNode.getTreeParent();
		if (expression == null)
			valParent.removeChild(valNode);
		else {
			valParent.replaceChild(valNode, expression.getNode().copyElement());
		}
		return keywordArg;
	}

	@Override
	public ASTNode createComma(Project project) {
		PsiFile dummyFile = createDummyFile(project, "[0,]");
		PyExpressionStatement expressionStatement = (PyExpressionStatement) dummyFile.getFirstChild();

		ASTNode zero = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();

		return zero.getTreeNext().copyElement();
	}

	@Override
	public PsiElement insertItemIntoList(Project project, PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert)
			throws IncorrectOperationException {
		ASTNode add = toInsert.getNode().copyElement();
		if (afterThis == null) {
			ASTNode exprNode = list.getNode();
			ASTNode[] closingTokens = exprNode.getChildren(TokenSet.create(PyTokenTypes.LBRACKET, PyTokenTypes.LPAR));

			if (closingTokens.length == 0) {
				exprNode.addChild(add);
			} else {
				ASTNode next = PyUtil.getNextNonWhitespace(closingTokens[(closingTokens.length - 1)]);
				if (next != null) {
					ASTNode comma = createComma(project);
					exprNode.addChild(comma, next);
					exprNode.addChild(add, comma);
				} else {
					exprNode.addChild(add);
				}
			}
		} else {
			ASTNode lastArgNode = afterThis.getNode();
			ASTNode comma = createComma(project);
			ASTNode parent = lastArgNode.getTreeParent();
			ASTNode afterLast = lastArgNode.getTreeNext();
			if (afterLast == null)
				parent.addChild(add);
			else {
				parent.addChild(add, afterLast);
			}
			parent.addChild(comma, add);
		}
		return add.getPsi();
	}

	@Override
	public PyBinaryExpression createBinaryExpression(Project project, String s, PyExpression expr, PyExpression listLiteral) {
		PsiFile dummyFile = createDummyFile(project, "a " + s + " b");
		PyExpressionStatement expressionStatement = (PyExpressionStatement) dummyFile.getFirstChild();

		PyBinaryExpression binExpr = (PyBinaryExpression) expressionStatement.getExpression();

		ASTNode binnode = binExpr.getNode();
		binnode.replaceChild(binExpr.getLeftExpression().getNode(), expr.getNode().copyElement());

		binnode.replaceChild(binExpr.getRightExpression().getNode(), listLiteral.getNode().copyElement());

		return binExpr;
	}

	@Override
	public PyCallExpression createCallExpression(Project project, String functionName) {
		PsiFile dummyFile = createDummyFile(project, functionName + "()");
		return (PyCallExpression) dummyFile.getFirstChild().getFirstChild();
	}

	@Override
	public PyExpressionStatement createExpressionStatement(Project project, PyExpression expr) {
		PsiFile dummyFile = createDummyFile(project, "x");
		PyExpressionStatement stmt = (PyExpressionStatement) dummyFile.getFirstChild();
		stmt.getNode().replaceChild(stmt.getExpression().getNode(), expr.getNode());
		return stmt;
	}

	@Override
	public void setStringValue(PyStringLiteralExpression string, String value) {
		ASTNode strNode = string.getNode();
		Project project = string.getProject();
		strNode.getTreeParent().replaceChild(strNode, createStringLiteralFromString(project, string.getContainingFile(), value).getNode());
	}
}