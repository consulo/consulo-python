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

package org.consulo.python.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PyParser implements PsiParser {
	private static final Logger LOGGER = Logger.getInstance(PyParser.class.getName());

	@Override
	@NotNull
	public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder, @NotNull LanguageVersion languageVersion) {
		builder.setDebugMode(false);
		long start = System.currentTimeMillis();
		PsiBuilder.Marker rootMarker = builder.mark();
		ParsingContext context = new ParsingContext();
		while (!builder.eof()) {
			context.getStatementParser().parseStatement(builder);
		}
		rootMarker.done(root);
		ASTNode ast = builder.getTreeBuilt();
		long diff = System.currentTimeMillis() - start;
		double kb = builder.getCurrentOffset() / 1000.0D;
		LOGGER.debug("Parsed " + String.format("%.1f", kb) + "K file in " + diff + "ms");
		return ast;
	}
}