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

package ru.yole.pythonid.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.PyElementTypes;
import ru.yole.pythonid.PyTokenTypes;
import ru.yole.pythonid.PythonLanguage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyBlock
		implements Block {
	private PythonLanguage _language;
	private Alignment _alignment;
	private Indent _indent;
	private ASTNode _node;
	private Wrap _wrap;
	private CodeStyleSettings _settings;
	private List<Block> _subBlocks = null;
	private PyElementTypes _elementTypes;
	private PyTokenTypes _tokenTypes;
	private Alignment _childListAlignment;
	private TokenSet _listElementTypes;
	private static final boolean DUMP_FORMATTING_BLOCKS = false;

	public PyBlock(PythonLanguage language, ASTNode node, Alignment alignment, Indent indent, Wrap wrap, CodeStyleSettings settings) {
		this._language = language;
		this._elementTypes = this._language.getElementTypes();
		this._tokenTypes = this._language.getTokenTypes();
		this._alignment = alignment;
		this._indent = indent;
		this._node = node;
		this._wrap = wrap;
		this._settings = settings;
		this._childListAlignment = Alignment.createAlignment();

		this._listElementTypes = TokenSet.create(new IElementType[]{this._elementTypes.LIST_LITERAL_EXPRESSION, this._elementTypes.LIST_COMP_EXPRESSION, this._elementTypes.DICT_LITERAL_EXPRESSION, this._elementTypes.ARGUMENT_LIST, this._elementTypes.PARAMETER_LIST});
	}

	@NotNull
	public ASTNode getNode() {
		ASTNode tmp4_1 = this._node;
		if (tmp4_1 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp4_1;
	}

	@NotNull
	public TextRange getTextRange() {
		TextRange tmp9_4 = this._node.getTextRange();
		if (tmp9_4 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp9_4;
	}

	@NotNull
	public List<Block> getSubBlocks() {
		if (this._subBlocks == null)
			this._subBlocks = buildSubBlocks();
		List tmp19_16 = this._subBlocks;
		if (tmp19_16 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp19_16;
	}

	private List<Block> buildSubBlocks() {
		List blocks = new ArrayList();
		for (ASTNode child = this._node.getFirstChildNode();
			 child != null; child = child.getTreeNext()) {
			IElementType childType = child.getElementType();

			if (child.getTextRange().getLength() != 0) {
				if (childType == TokenType.WHITE_SPACE) {
					if (child.getText().indexOf('\\') >= 0) {
						return Collections.emptyList();
					}

				} else {
					blocks.add(buildSubBlock(child));
				}
			}
		}
		return Collections.unmodifiableList(blocks);
	}

	private PyBlock buildSubBlock(ASTNode child) {
		IElementType parentType = this._node.getElementType();
		IElementType childType = child.getElementType();
		Wrap wrap = null;
		Indent childIndent = Indent.getNoneIndent();
		Alignment childAlignment = null;
		if ((childType == this._elementTypes.STATEMENT_LIST) || (childType == this._elementTypes.IMPORT_ELEMENT)) {
			if (hasLineBreakBefore(child)) {
				childIndent = Indent.getNormalIndent();
			}
		}
		if (this._listElementTypes.contains(parentType)) {
			wrap = Wrap.createWrap(WrapType.NORMAL, true);
			if ((!this._tokenTypes.OPEN_BRACES.contains(childType)) && (!this._tokenTypes.CLOSE_BRACES.contains(childType))) {
				childAlignment = this._childListAlignment;
			}
		}
		if ((parentType == this._elementTypes.LIST_LITERAL_EXPRESSION) || (parentType == this._elementTypes.ARGUMENT_LIST)) {
			childIndent = Indent.getContinuationIndent();
		}

		return new PyBlock(this._language, child, childAlignment, childIndent, wrap, this._settings);
	}

	private boolean hasLineBreakBefore(ASTNode child) {
		ASTNode prevNode = child.getTreePrev();
		if ((prevNode != null) && (prevNode.getElementType() == TokenType.WHITE_SPACE)) {
			String prevNodeText = prevNode.getText();
			if (prevNodeText.indexOf('\n') >= 0) {
				return true;
			}
		}
		return false;
	}

	private void dumpSubBlocks() {
		System.out.println("Subblocks of " + this._node.getPsi() + ":");
		for (Block block : this._subBlocks)
			if ((block instanceof PyBlock)) {
				System.out.println("  " + ((PyBlock) block).getNode().getPsi().toString() + " " + block.getTextRange().getStartOffset() + ":" + block.getTextRange().getLength());
			} else {
				System.out.println("  <unknown block>");
			}
	}

	@Nullable
	public Wrap getWrap() {
		return this._wrap;
	}

	@Nullable
	public Indent getIndent() {
		assert (this._indent != null);
		return this._indent;
	}

	@Nullable
	public Alignment getAlignment() {
		return this._alignment;
	}

	@Nullable
	public Spacing getSpacing(Block child1, Block child2) {
		ASTNode childNode1 = ((PyBlock) child1).getNode();
		ASTNode childNode2 = ((PyBlock) child2).getNode();
		IElementType parentType = this._node.getElementType();
		IElementType type1 = childNode1.getElementType();
		IElementType type2 = childNode2.getElementType();

		return null;
	}

	@NotNull
	public ChildAttributes getChildAttributes(int newChildIndex) {
		PyBlock insertAfterBlock = (PyBlock) this._subBlocks.get(newChildIndex - 1);
		ASTNode lastChild = insertAfterBlock.getNode();

		int statementListsBelow = 0;

		while (lastChild != null) {
			if ((lastChild.getElementType() == this._elementTypes.STATEMENT_LIST) && (hasLineBreakBefore(lastChild))) {
				statementListsBelow++;
			} else if ((statementListsBelow > 0) && ((lastChild.getPsi() instanceof PsiErrorElement))) {
				statementListsBelow++;
			}
			if ((this._node.getElementType() == this._elementTypes.STATEMENT_LIST) && ((lastChild.getPsi() instanceof PsiErrorElement))) {
				ChildAttributes tmp112_109 = ChildAttributes.DELEGATE_TO_PREV_CHILD;
				if (tmp112_109 == null) throw new IllegalStateException("@NotNull method must not return null");
				return tmp112_109;
			}
			lastChild = getLastNonSpaceChild(lastChild, true);
		}

		if (statementListsBelow > 1) {
			int indent = this._settings.getIndentSize(this._language.getFileType());
			void tmp174_171 = new ChildAttributes(Indent.getSpaceIndent(indent * statementListsBelow), null);
			if (tmp174_171 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp174_171;
		}
		void tmp205_202 = new ChildAttributes(getChildIndent(newChildIndex), getChildAlignment());
		if (tmp205_202 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp205_202;
	}

	private Alignment getChildAlignment() {
		if (this._listElementTypes.contains(this._node.getElementType())) {
			return this._childListAlignment;
		}
		return null;
	}

	private Indent getChildIndent(int newChildIndex) {
		ASTNode lastChild = getLastNonSpaceChild(this._node, false);
		if ((lastChild != null) && (lastChild.getElementType() == this._elementTypes.STATEMENT_LIST)) {
			PyBlock insertAfterBlock = (PyBlock) this._subBlocks.get(newChildIndex - 1);
			ASTNode afterNode = insertAfterBlock.getNode();

			if ((afterNode.getElementType() == this._elementTypes.STATEMENT_LIST) || (afterNode.getElementType() == this._tokenTypes.COLON)) {
				return Indent.getNormalIndent();
			}

			ASTNode lastFirstChild = lastChild.getFirstChildNode();
			if ((lastFirstChild != null) && (lastFirstChild == lastChild.getLastChildNode()) && ((lastFirstChild.getPsi() instanceof PsiErrorElement))) {
				return Indent.getNormalIndent();
			}
		}

		if (this._listElementTypes.contains(this._node.getElementType())) {
			return Indent.getNormalIndent();
		}

		return Indent.getNoneIndent();
	}

	private static ASTNode getLastNonSpaceChild(ASTNode node, boolean acceptError) {
		ASTNode lastChild = node.getLastChildNode();
		while ((lastChild != null) && ((lastChild.getElementType() == TokenType.WHITE_SPACE) || ((!acceptError) && ((lastChild.getPsi() instanceof PsiErrorElement))))) {
			lastChild = lastChild.getTreePrev();
		}
		return lastChild;
	}

	public boolean isIncomplete() {
		ASTNode lastChild = getLastNonSpaceChild(this._node, false);
		if ((lastChild != null) && (lastChild.getElementType() == this._elementTypes.STATEMENT_LIST)) {
			ASTNode statementListPrev = lastChild.getTreePrev();
			if ((statementListPrev != null) && (statementListPrev.getText().indexOf('\n') >= 0)) {
				return true;
			}
		}

		return false;
	}

	public boolean isLeaf() {
		return this._node.getFirstChildNode() == null;
	}
}