/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.spellchecker;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.spellcheker.SpellcheckingStrategy;
import consulo.language.spellcheker.tokenizer.TokenConsumer;
import consulo.language.spellcheker.tokenizer.Tokenizer;
import consulo.language.spellcheker.tokenizer.splitter.PlainTextTokenSplitter;
import consulo.language.spellcheker.tokenizer.splitter.TokenSplitter;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.StringNodeInfo;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonSpellcheckerStrategy extends SpellcheckingStrategy
{
	private static class StringLiteralTokenizer extends Tokenizer<PyStringLiteralExpression>
	{
		@Override
		public void tokenize(@Nonnull PyStringLiteralExpression element, TokenConsumer consumer)
		{
			final TokenSplitter splitter = PlainTextTokenSplitter.getInstance();
			final List<ASTNode> strNodes = element.getStringNodes();
			final List<String> prefixes = ContainerUtil.mapNotNull(strNodes, n -> StringUtil.nullize(new StringNodeInfo(n).getPrefix()));

			if(element.textContains('\\') && !prefixes.stream().anyMatch(PyStringLiteralUtil::isRawPrefix))
			{
				for(Pair<TextRange, String> fragment : element.getDecodedFragments())
				{
					final String value = fragment.getSecond();
					final int startOffset = fragment.getFirst().getStartOffset();
					consumer.consumeToken(element, value, false, startOffset, TextRange.allOf(value), splitter);
				}
			}
			else if(!prefixes.isEmpty())
			{
				for(TextRange valueTextRange : element.getStringValueTextRanges())
				{
					final String value = valueTextRange.substring(element.getText());
					final int startOffset = valueTextRange.getStartOffset();
					consumer.consumeToken(element, value, false, startOffset, TextRange.allOf(value), splitter);
				}
			}
			else
			{
				consumer.consumeToken(element, splitter);
			}
		}
	}

	private static class FormatStringTokenizer extends Tokenizer<PyStringLiteralExpression>
	{
		@Override
		public void tokenize(@Nonnull PyStringLiteralExpression element, TokenConsumer consumer)
		{
			String stringValue = element.getStringValue();
			List<PyStringFormatParser.FormatStringChunk> chunks = PyStringFormatParser.parsePercentFormat(stringValue);
			TokenSplitter splitter = PlainTextTokenSplitter.getInstance();
			for(PyStringFormatParser.FormatStringChunk chunk : chunks)
			{
				if(chunk instanceof PyStringFormatParser.ConstantChunk)
				{
					int startIndex = element.valueOffsetToTextOffset(chunk.getStartIndex());
					int endIndex = element.valueOffsetToTextOffset(chunk.getEndIndex());
					String text = element.getText().substring(startIndex, endIndex);
					consumer.consumeToken(element, text, false, startIndex, TextRange.allOf(text), splitter);
				}
			}
		}
	}

	private StringLiteralTokenizer myStringLiteralTokenizer = new StringLiteralTokenizer();
	private FormatStringTokenizer myFormatStringTokenizer = new FormatStringTokenizer();

	@Nonnull
	@Override
	public Tokenizer getTokenizer(PsiElement element)
	{
		if(element instanceof PyStringLiteralExpression)
		{
			PsiElement parent = element.getParent();
			if(parent instanceof PyBinaryExpression)
			{
				PyBinaryExpression binaryExpression = (PyBinaryExpression) parent;
				if(element == binaryExpression.getLeftExpression() && binaryExpression.getOperator() == PyTokenTypes.PERC)
				{
					return myFormatStringTokenizer;
				}
			}
			return myStringLiteralTokenizer;
		}
		return super.getTokenizer(element);
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
