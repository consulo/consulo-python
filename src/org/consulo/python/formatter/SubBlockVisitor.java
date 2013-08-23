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

package org.consulo.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import org.consulo.python.*;

import java.util.ArrayList;
import java.util.List;

public class SubBlockVisitor extends PyNodeVisitor {
	private PythonLanguage language;
	private CodeStyleSettings _settings;
	private List<Block> _blocks = new ArrayList();

	public SubBlockVisitor(PythonLanguage language, CodeStyleSettings settings) {
		this.language = language;
		this._settings = settings;
	}

	public List<Block> getBlocks() {
		return this._blocks;
	}

	@Override
	public void visitElement(ASTNode node) {
		ASTNode child = node.getFirstChildNode();
		Alignment align1 = Alignment.createAlignment();
		Alignment align2 = Alignment.createAlignment();
		IElementType parentType = node.getElementType();

		while (child != null) {
			IElementType childType = child.getElementType();
			if ((!PythonTokenSets.WHITESPACE.contains(childType)) && (childType != TokenType.WHITE_SPACE) && (child.getTextRange().getLength() > 0)) {
				Wrap wrap = null;
				Indent childIndent = Indent.getNoneIndent();
				Alignment childAlignment = null;
				if (childType == PyElementTypes.STATEMENT_LIST) {
					ASTNode prevNode = child.getTreePrev();
					if ((prevNode != null) && (prevNode.getElementType() == TokenType.WHITE_SPACE)) {
						String prevNodeText = prevNode.getText();
						if (prevNodeText.indexOf('\n') >= 0) {
							childIndent = Indent.getNormalIndent();
						}
					}
				}
				if (parentType == PyElementTypes.LIST_LITERAL_EXPRESSION) {
					wrap = Wrap.createWrap(WrapType.NORMAL, true);
					if ((childType == PyTokenTypes.LBRACKET) || (childType == PyTokenTypes.RBRACKET)) {
						childAlignment = align2;
					} else childAlignment = align1;
				}

				if (parentType == PyElementTypes.ARGUMENT_LIST) {
					if ((childType == PyTokenTypes.LPAR) || (childType == PyTokenTypes.RPAR))
						childAlignment = align2;
					else {
						childAlignment = align1;
					}
				}
				if ((parentType == PyElementTypes.LIST_LITERAL_EXPRESSION) || (parentType == PyElementTypes.ARGUMENT_LIST)) {
					childIndent = Indent.getContinuationIndent();
				}

				this._blocks.add(new PyBlock(child, childAlignment, childIndent, wrap, this._settings));
			}
			child = child.getTreeNext();
		}
	}
}