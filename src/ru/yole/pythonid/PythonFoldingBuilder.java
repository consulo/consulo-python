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

package ru.yole.pythonid;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.List;

public class PythonFoldingBuilder
		implements FoldingBuilder {
	private AbstractPythonLanguage language;

	public PythonFoldingBuilder(AbstractPythonLanguage language) {
		this.language = language;
	}

	@Override
	public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
		List descriptors = new ArrayList();
		appendDescriptors(node, descriptors);
		return (FoldingDescriptor[]) descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
	}

	private void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
		if (node.getElementType() == this.language.getElementTypes().STATEMENT_LIST) {
			IElementType elType = node.getTreeParent().getElementType();
			if ((elType == this.language.getElementTypes().FUNCTION_DECLARATION) || (elType == this.language.getElementTypes().CLASS_DECLARATION)) {
				ASTNode colon = node.getTreeParent().findChildByType(this.language.getTokenTypes().COLON);
				if (colon != null) {
					descriptors.add(new FoldingDescriptor(node, new TextRange(colon.getStartOffset() + 1, node.getStartOffset() + node.getTextLength())));
				} else {
					descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
				}

			}

		}

		ASTNode child = node.getFirstChildNode();
		while (child != null) {
			appendDescriptors(child, descriptors);
			child = child.getTreeNext();
		}
	}

	@Override
	public String getPlaceholderText(ASTNode node) {
		return "...";
	}

	@Override
	public boolean isCollapsedByDefault(ASTNode node) {
		return false;
	}
}