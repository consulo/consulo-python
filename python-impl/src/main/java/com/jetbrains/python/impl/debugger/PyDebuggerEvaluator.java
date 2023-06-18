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
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.impl.console.PyConsoleIndentUtil;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public class PyDebuggerEvaluator extends XDebuggerEvaluator
{
	private static final PyDebugValue NONE = new PyDebugValue("", "NoneType", null, "None", false, false, false, false, null, null);

	private Project myProject;
	private final PyFrameAccessor myDebugProcess;

	public PyDebuggerEvaluator(@Nonnull Project project, @Nonnull final PyFrameAccessor debugProcess)
	{
		myProject = project;
		myDebugProcess = debugProcess;
	}

	@Override
	public void evaluate(@Nonnull String expression, @Nonnull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition)
	{
		doEvaluate(expression, callback, true);
	}

	private void doEvaluate(final String expr, final XEvaluationCallback callback, final boolean doTrunc)
	{
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			String expression = expr.trim();
			if(expression.isEmpty())
			{
				callback.evaluated(NONE);
				return;
			}

			final boolean isExpression = PyDebugSupportUtils.isExpression(myProject, expression);
			try
			{
				// todo: think on getting results from EXEC
				final PyDebugValue value = myDebugProcess.evaluate(expression, !isExpression, doTrunc);
				if(value.isErrorOnEval())
				{
					callback.errorOccurred("{" + value.getType() + "}" + value.getValue());
				}
				else
				{
					callback.evaluated(value);
				}
			}
			catch(PyDebuggerException e)
			{
				callback.errorOccurred(e.getTracebackError());
			}
		});
	}

	@Nullable
	@Override
	public TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset, boolean sideEffectsAllowed)
	{
		return PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset);
	}

	@Nonnull
	@Override
	public String formatTextForEvaluation(@Nonnull String text)
	{
		return PyConsoleIndentUtil.normalize(text);
	}
}
