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

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.parsing.PyParser;
import ru.yole.pythonid.psi.PyElementType;
import ru.yole.pythonid.psi.impl.PyElementTypeImpl;

public class PythonParserDefinition
		implements ParserDefinition {
	private static final Object NULL_OBJECT = new Object();
	private AbstractPythonLanguage language;
	private FileType pythonFileType;
	private TokenSet myWhitespaceTokens;
	private TokenSet myCommentTokens;

	public PythonParserDefinition(AbstractPythonLanguage language, FileType fileType) {
		this.language = language;
		this.pythonFileType = fileType;

		PyTokenTypes tokenTypes = language.getTokenTypes();
		this.myWhitespaceTokens = TokenSet.create(new IElementType[]{tokenTypes.LINE_BREAK, tokenTypes.SPACE, tokenTypes.TAB, tokenTypes.FORMFEED});

		this.myCommentTokens = TokenSet.create(new IElementType[]{language.getTokenTypes().END_OF_LINE_COMMENT});
	}

	@NotNull
	public Lexer createLexer(Project project) {
		void tmp11_8 = new PythonIndentingLexer(this.language);
		if (tmp11_8 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp11_8;
	}

	@Override
	public IFileElementType getFileNodeType() {
		return this.language.getFileElementType();
	}

	@NotNull
	public TokenSet getWhitespaceTokens() {
		TokenSet tmp4_1 = this.myWhitespaceTokens;
		if (tmp4_1 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp4_1;
	}

	@NotNull
	public TokenSet getCommentTokens() {
		TokenSet tmp4_1 = this.myCommentTokens;
		if (tmp4_1 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp4_1;
	}

	@NotNull
	public PsiParser createParser(Project project) {
		void tmp11_8 = new PyParser(this.language);
		if (tmp11_8 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp11_8;
	}

	public PsiFile createFile(Project project, VirtualFile file) {
		return this.language.getFileCreator().createFile(project, file);
	}

	public PsiFile createFile(Project project, String name, CharSequence text) {
		return this.language.getFileCreator().createFile(project, name, text);
	}

	@Override
	@NotNull
	public PsiElement createElement(ASTNode node) {
		IElementType type = node.getElementType();
		if ((type instanceof PyElementTypeImpl)) {
			PyElementType pyElType = (PyElementType) type;
			PsiElement tmp25_22 = createElement(pyElType, node);
			if (tmp25_22 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp25_22;
		}
		void tmp48_45 = new ASTWrapperPsiElement(node);
		if (tmp48_45 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp48_45;
	}

	private PsiElement createElement(PyElementType type, ASTNode node) {
		if ((type instanceof PyElementTypeImpl)) {
			PyElementTypeImpl realtype = (PyElementTypeImpl) type;

			PsiElement element = realtype.createElement(node, this.language);

			return element;
		}
		return null;
	}
}