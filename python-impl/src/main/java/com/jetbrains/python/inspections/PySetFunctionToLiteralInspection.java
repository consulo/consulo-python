/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.ReplaceFunctionWithSetLiteralQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * User: catherine
 * <p>
 * Inspection to find set built-in function and replace it with set literal
 * available if the selected language level supports set literals.
 */
@ExtensionImpl
public class PySetFunctionToLiteralInspection extends PyInspection
{

	@Nls
	@Nonnull
	@Override
	public String getDisplayName()
	{
		return PyBundle.message("INSP.NAME.set.function.to.literal");
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		return new Visitor(holder, session);
	}

	private static class Visitor extends PyInspectionVisitor
	{
		public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session)
		{
			super(holder, session);
		}

		@Override
		public void visitPyCallExpression(final PyCallExpression node)
		{
			if(!isAvailable(node))
			{
				return;
			}
			PyExpression callee = node.getCallee();
			if(node.isCalleeText(PyNames.SET) && callee != null && PyBuiltinCache.isInBuiltins(callee))
			{
				PyExpression[] arguments = node.getArguments();
				if(arguments.length == 1)
				{
					PyElement[] elements = getSetCallArguments(node);
					if(elements.length != 0)
					{
						registerProblem(node, PyBundle.message("INSP.NAME.set.function.to.literal"), new ReplaceFunctionWithSetLiteralQuickFix());
					}
				}
			}
		}

		private static boolean isAvailable(PyCallExpression node)
		{
			final InspectionProfile profile = InspectionProjectProfileManager.getInstance(node.getProject()).getInspectionProfile();
			final InspectionToolWrapper inspectionTool = profile.getInspectionTool("PyCompatibilityInspection", node.getProject());
			if(inspectionTool != null)
			{
				final Object inspection = inspectionTool.getState();
				if(inspection instanceof PyCompatibilityInspectionState)
				{
					final List<String> versions = ((PyCompatibilityInspectionState) inspection).versions;
					for(String s : versions)
					{
						if(!LanguageLevel.fromPythonVersion(s).supportsSetLiterals())
						{
							return false;
						}
					}
				}
			}
			return LanguageLevel.forElement(node).supportsSetLiterals();
		}
	}

	public static PyElement[] getSetCallArguments(PyCallExpression node)
	{
		PyExpression argument = node.getArguments()[0];
		if(argument instanceof PyStringLiteralExpression)
		{
			return PyElement.EMPTY_ARRAY;
		}
		if((argument instanceof PySequenceExpression || (argument instanceof PyParenthesizedExpression && ((PyParenthesizedExpression) argument)
				.getContainedExpression() instanceof
				PyTupleExpression)))
		{

			if(argument instanceof PySequenceExpression)
			{
				return ((PySequenceExpression) argument).getElements();
			}
			PyExpression tuple = ((PyParenthesizedExpression) argument).getContainedExpression();
			if(tuple instanceof PyTupleExpression)
			{
				return ((PyTupleExpression) (tuple)).getElements();
			}
		}
		return PyElement.EMPTY_ARRAY;
	}
}
