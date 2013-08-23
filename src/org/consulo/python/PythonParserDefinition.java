/*
 * Copyright 2006 Dmitry Jemerov (yole)
 * Copyright 2013 Consulo Org
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

package org.consulo.python;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.consulo.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;
import org.consulo.python.parsing.PyParser;
import org.consulo.python.psi.PyElementType;

public class PythonParserDefinition implements ParserDefinition
{
	public final static IFileElementType FILE_ELEMENT_TYPE = new IFileElementType("PYTHON_FILE", PythonLanguage.INSTANCE);

	@Override
	@NotNull
	public Lexer createLexer(Project project, @NotNull LanguageVersion languageVersion) {
		return new PythonIndentingLexer();
	}

	@NotNull
	@Override
	public IFileElementType getFileNodeType() {
		return FILE_ELEMENT_TYPE;
	}

	@Override
	@NotNull
	public TokenSet getWhitespaceTokens(@NotNull LanguageVersion languageVersion) {
		return PythonTokenSets.WHITESPACE_OR_LINEBREAK;
	}

	@Override
	@NotNull
	public TokenSet getCommentTokens(@NotNull LanguageVersion languageVersion) {
		return PythonTokenSets.COMMENTS;
	}

	@NotNull
	@Override
	public TokenSet getStringLiteralElements(@NotNull LanguageVersion languageVersion) {
		return PythonTokenSets.STRINGS;
	}

	@Override
	@NotNull
	public PsiParser createParser(Project project, @NotNull LanguageVersion languageVersion) {
		return new PyParser();
	}

	@Override
	@NotNull
	public PsiElement createElement(ASTNode node) {
		IElementType type = node.getElementType();
		if ((type instanceof PyElementType)) {
			PyElementType pyElType = (PyElementType) type;

			return pyElType.createElement(node);
		}
		return new ASTWrapperPsiElement(node);
	}

	@Override
	public PsiFile createFile(FileViewProvider fileViewProvider) {
		return new PyFileImpl(fileViewProvider);
	}

	@Override
	public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode astNode, ASTNode astNode2) {
		return SpaceRequirements.MAY;
	}
}