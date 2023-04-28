/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.inspections;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * User : catherine
 */
@ExtensionImpl
public class PyNonAsciiCharInspection extends PyInspection
{
	@Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new PyNonAsciiCharInspectionState();
	}

	@Nonnull
	@Override
	public String getDisplayName()
	{
		return PyBundle.message("INSP.NAME.non.ascii");
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		return new Visitor(holder, session, (PyNonAsciiCharInspectionState) state);
	}

	private class Visitor extends PyInspectionVisitor
	{
		private final PyNonAsciiCharInspectionState myState;

		public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session, PyNonAsciiCharInspectionState state)
		{
			super(holder, session);
			myState = state;
		}

		@Override
		public void visitComment(PsiComment node)
		{
			checkString(node, node.getText());
		}

		private void checkString(PsiElement node, String value)
		{
			if(LanguageLevel.forElement(node).isPy3K())
			{
				return;
			}
			PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
			if(file == null)
			{
				return;
			}
			final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(file.getText());

			boolean hasNonAscii = false;

			CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder();
			int length = value.length();
			char c = 0;
			for(int i = 0; i < length; ++i)
			{
				c = value.charAt(i);
				if(!asciiEncoder.canEncode(c))
				{
					hasNonAscii = true;
					break;
				}
			}

			if(hasNonAscii)
			{
				if(charsetString == null)
				{
					registerProblem(node, "Non-ASCII character " + c + " in file, but no encoding declared",
							new AddEncodingQuickFix(myState.myDefaultEncoding, myState.myEncodingFormatIndex));
				}
			}
		}

		@Override
		public void visitPyStringLiteralExpression(PyStringLiteralExpression node)
		{
			checkString(node, node.getText());
		}
	}
}
