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
package com.jetbrains.python.impl.documentation.docstrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.docstrings.DocStringParameterReference.ReferenceType;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.toolbox.Substring;

/**
 * @author yole
 */
public class DocStringReferenceProvider extends PsiReferenceProvider
{
	@Nonnull
	@Override
	public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context)
	{
		if(element == DocStringUtil.getParentDefinitionDocString(element))
		{
			PyStringLiteralExpression expr = (PyStringLiteralExpression) element;
			List<TextRange> ranges = expr.getStringValueTextRanges();

			String exprText = expr.getText();
			TextRange textRange = PyStringLiteralExpressionImpl.getNodeTextRange(exprText);
			String text = textRange.substring(exprText);

			if(!ranges.isEmpty())
			{
				List<PsiReference> result = new ArrayList<>();
				int offset = ranges.get(0).getStartOffset();
				// XXX: It does not work with multielement docstrings
				StructuredDocString docString = DocStringUtil.parse(text, element);
				if(docString instanceof TagBasedDocString)
				{
					TagBasedDocString taggedDocString = (TagBasedDocString) docString;
					result.addAll(referencesFromNames(expr, offset, docString, taggedDocString.getTagArguments(TagBasedDocString.PARAM_TAGS), ReferenceType.PARAMETER));
					result.addAll(referencesFromNames(expr, offset, docString, taggedDocString.getTagArguments(TagBasedDocString.PARAM_TYPE_TAGS), ReferenceType.PARAMETER_TYPE));
					result.addAll(referencesFromNames(expr, offset, docString, docString.getKeywordArgumentSubstrings(), ReferenceType.KEYWORD));

					result.addAll(referencesFromNames(expr, offset, docString, taggedDocString.getTagArguments("var"), ReferenceType.VARIABLE));
					result.addAll(referencesFromNames(expr, offset, docString, taggedDocString.getTagArguments("cvar"), ReferenceType.CLASS_VARIABLE));
					result.addAll(referencesFromNames(expr, offset, docString, taggedDocString.getTagArguments("ivar"), ReferenceType.INSTANCE_VARIABLE));
					result.addAll(returnTypes(element, docString, offset));
				}
				else if(docString instanceof SectionBasedDocString)
				{
					SectionBasedDocString sectioned = (SectionBasedDocString) docString;
					result.addAll(referencesFromFields(expr, offset, sectioned.getParameterFields(), ReferenceType.PARAMETER));
					result.addAll(referencesFromFields(expr, offset, sectioned.getKeywordArgumentFields(), ReferenceType.KEYWORD));
					result.addAll(referencesFromFields(expr, offset, sectioned.getAttributeFields(), PyUtil.isTopLevel(element) ? ReferenceType.GLOBAL_VARIABLE : ReferenceType.INSTANCE_VARIABLE));
					result.addAll(referencesFromFields(expr, offset, sectioned.getReturnFields(), null));
				}
				return result.toArray(new PsiReference[result.size()]);
			}
		}
		return PsiReference.EMPTY_ARRAY;
	}

	private static List<PsiReference> returnTypes(PsiElement element, StructuredDocString docString, int offset)
	{
		List<PsiReference> result = new ArrayList<>();

		Substring rtype = docString.getReturnTypeSubstring();
		if(rtype != null)
		{
			result.addAll(parseTypeReferences(element, rtype, offset));
		}
		return result;
	}

	private static List<PsiReference> referencesFromNames(@Nonnull PyStringLiteralExpression element,
			int offset,
			@Nonnull StructuredDocString docString,
			@Nonnull List<Substring> paramNames,
			@Nonnull ReferenceType refType)
	{
		List<PsiReference> result = new ArrayList<>();
		for(Substring name : paramNames)
		{
			String s = name.toString();
			if(PyNames.isIdentifier(s))
			{
				TextRange range = name.getTextRange().shiftRight(offset);
				result.add(new DocStringParameterReference(element, range, refType));
			}
			if(refType.equals(ReferenceType.PARAMETER_TYPE))
			{
				Substring type = docString.getParamTypeSubstring(s);
				if(type != null)
				{
					result.addAll(parseTypeReferences(element, type, offset));
				}
			}
		}
		return result;
	}

	@Nonnull
	private static List<PsiReference> referencesFromFields(@Nonnull PyStringLiteralExpression element,
			int offset,
			@Nonnull List<SectionBasedDocString.SectionField> fields,
			@Nullable ReferenceType nameRefType)
	{
		List<PsiReference> result = new ArrayList<>();
		for(SectionBasedDocString.SectionField field : fields)
		{
			for(Substring nameSub : field.getNamesAsSubstrings())
			{
				if(nameRefType != null && nameSub != null && !nameSub.isEmpty())
				{
					TextRange range = nameSub.getTextRange().shiftRight(offset);
					result.add(new DocStringParameterReference(element, range, nameRefType));
				}
			}
			Substring typeSub = field.getTypeAsSubstring();
			if(typeSub != null && !typeSub.isEmpty())
			{
				result.addAll(parseTypeReferences(element, typeSub, offset));
			}
		}
		return result;
	}

	@Nonnull
	private static List<PsiReference> parseTypeReferences(@Nonnull PsiElement anchor, @Nonnull Substring s, int offset)
	{
		List<PsiReference> result = new ArrayList<>();
		PyTypeParser.ParseResult parseResult = PyTypeParser.parse(anchor, s.toString());
		Map<TextRange, ? extends PyType> types = parseResult.getTypes();
		if(types.isEmpty())
		{
			result.add(new DocStringTypeReference(anchor, s.getTextRange().shiftRight(offset), s.getTextRange().shiftRight(offset), null, null));
		}
		offset = s.getTextRange().getStartOffset() + offset;
		Map<? extends PyType, TextRange> fullRanges = parseResult.getFullRanges();
		for(Map.Entry<TextRange, ? extends PyType> pair : types.entrySet())
		{
			PyType t = pair.getValue();
			TextRange range = pair.getKey().shiftRight(offset);
			TextRange fullRange = fullRanges.containsKey(t) ? fullRanges.get(t).shiftRight(offset) : range;
			PyImportElement importElement = parseResult.getImports().get(t);
			result.add(new DocStringTypeReference(anchor, range, fullRange, t, importElement));
		}
		return result;
	}

	@Nullable
	public static TextRange findNextTag(String docString, int pos, String[] paramTags)
	{
		int result = Integer.MAX_VALUE;
		String foundTag = null;
		for(String paramTag : paramTags)
		{
			int tagPos = docString.indexOf(paramTag, pos);
			while(tagPos >= 0 && tagPos + paramTag.length() < docString.length() &&
					Character.isLetterOrDigit(docString.charAt(tagPos + paramTag.length())))
			{
				tagPos = docString.indexOf(paramTag, tagPos + 1);
			}
			if(tagPos >= 0 && tagPos < result)
			{
				foundTag = paramTag;
				result = tagPos;
			}
		}
		return foundTag == null ? null : new TextRange(result, result + foundTag.length());
	}
}
