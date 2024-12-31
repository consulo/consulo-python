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
package com.jetbrains.python.impl.inspections.quickfix;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiComment;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author lada
 */
public class PyMakeFunctionReturnTypeQuickFix implements LocalQuickFix
{
	private final SmartPsiElementPointer<PyFunction> myFunction;
	private final SmartPsiElementPointer<PyAnnotation> myAnnotation;
	private final SmartPsiElementPointer<PsiComment> myTypeCommentAnnotation;
	private final String myReturnTypeName;

	public PyMakeFunctionReturnTypeQuickFix(@Nonnull PyFunction function, @Nullable String returnTypeName, @Nonnull TypeEvalContext context)
	{
		final SmartPointerManager manager = SmartPointerManager.getInstance(function.getProject());
		myFunction = manager.createSmartPsiElementPointer(function);
		PyAnnotation annotation = function.getAnnotation();
		myAnnotation = annotation != null ? manager.createSmartPsiElementPointer(annotation) : null;
		PsiComment typeCommentAnnotation = function.getTypeComment();
		myTypeCommentAnnotation = typeCommentAnnotation != null ? manager.createSmartPsiElementPointer(typeCommentAnnotation) : null;
		myReturnTypeName = (returnTypeName == null) ? PythonDocumentationProvider.getTypeName(function.getReturnStatementType(context), context) : returnTypeName;
	}

	@Nonnull
	public String getName()
	{
		PyFunction function = myFunction.getElement();
		String functionName = function != null ? function.getName() : "function";
		return PyBundle.message("QFIX.NAME.make.$0.return.$1", functionName, myReturnTypeName);
	}

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.make.$0.return.$1", "function", "inferred type");
	}

	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
		if(myAnnotation != null)
		{
			final PyAnnotation annotation = myAnnotation.getElement();
			if(annotation != null)
			{
				final PyExpression annotationExpr = annotation.getValue();
				if(annotationExpr == null)
				{
					return;
				}
				annotationExpr.replace(elementGenerator.createExpressionFromText(LanguageLevel.PYTHON30, myReturnTypeName));
			}
		}
		else if(myTypeCommentAnnotation != null)
		{
			final PsiComment typeComment = myTypeCommentAnnotation.getElement();
			if(typeComment != null)
			{
				final StringBuilder typeCommentAnnotation = new StringBuilder(typeComment.getText());
				typeCommentAnnotation.delete(typeCommentAnnotation.indexOf("->"), typeCommentAnnotation.length());
				typeCommentAnnotation.append("-> ").append(myReturnTypeName);
				final PsiComment newTypeComment = elementGenerator.createFromText(LanguageLevel.PYTHON27, PsiComment.class, typeCommentAnnotation.toString());
				typeComment.replace(newTypeComment);
			}
		}
	}
}
