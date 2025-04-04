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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * User: ktisha
 * <p>
 * Helps to specify type by assertion
 */
public class TypeAssertionIntention extends PyBaseIntentionAction
{

	@Nonnull
	public String getText()
	{
		return PyBundle.message("INTN.insert.assertion");
	}

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("INTN.insert.assertion");
	}

	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		if(!(file instanceof PyFile))
		{
			return false;
		}

		PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
		PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
		if(problemElement == null)
		{
			return false;
		}
		if(problemElement.getParent() instanceof PyWithItem)
		{
			return false;
		}
		final PyExpression qualifier = ((PyQualifiedExpression) problemElement).getQualifier();
		if(qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF))
		{
			problemElement = qualifier;
		}
		final PsiReference reference = problemElement.getReference();
		if(problemElement.getParent() instanceof PyCallExpression ||
				PsiTreeUtil.getParentOfType(problemElement, PyComprehensionElement.class) != null ||
				PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null ||
				PsiTreeUtil.getParentOfType(problemElement, PyGeneratorExpression.class) != null ||
				(reference != null && reference.resolve() == null))
		{
			return false;
		}
		final PyType type = TypeEvalContext.codeAnalysis(file.getProject(), file).getType(problemElement);
		return type == null;
	}

	@Override
	public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
	{
		PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
		PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
		if(problemElement != null)
		{
			PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

			String name = problemElement.getText();
			final PyExpression qualifier = ((PyQualifiedExpression) problemElement).getQualifier();
			if(qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF))
			{
				final String referencedName = ((PyQualifiedExpression) problemElement).getReferencedName();
				if(referencedName == null || PyNames.GETITEM.equals(referencedName))
				{
					name = qualifier.getText();
				}
			}

			final String text = "assert isinstance(" + name + ", )";
			PyAssertStatement assertStatement = elementGenerator.createFromText(LanguageLevel.forElement(problemElement), PyAssertStatement.class, text);

			final PsiElement parentStatement = PsiTreeUtil.getParentOfType(problemElement, PyStatement.class);
			if(parentStatement == null)
			{
				return;
			}
			final PsiElement parent = parentStatement.getParent();
			PsiElement element;
			if(parentStatement instanceof PyAssignmentStatement && ((PyAssignmentStatement) parentStatement).getTargets()[0] == problemElement)
			{
				element = parent.addAfter(assertStatement, parentStatement);
			}
			else
			{
				PyStatementList statementList = PsiTreeUtil.getParentOfType(parentStatement, PyStatementList.class);
				final Document document = editor.getDocument();

				if(statementList != null)
				{
					PsiElement statementListParent = PsiTreeUtil.getParentOfType(statementList, PyStatement.class);
					if(statementListParent != null && document.getLineNumber(statementList.getTextOffset()) == document.getLineNumber(statementListParent.getTextOffset()))
					{
						final String substring = TextRange.create(statementListParent.getTextRange().getStartOffset(), statementList.getTextOffset()).substring(document.getText());
						final PyStatement foo = elementGenerator.createFromText(LanguageLevel.forElement(problemElement), PyStatement.class, substring + "\n\t" +
								text + "\n\t" + statementList.getText());

						statementListParent = statementListParent.replace(foo);
						statementList = PsiTreeUtil.findChildOfType(statementListParent, PyStatementList.class);
						assert statementList != null;
						element = statementList.getStatements()[0];
					}
					else
					{
						element = parent.addBefore(assertStatement, parentStatement);
					}
				}
				else
				{
					element = parent.addBefore(assertStatement, parentStatement);
				}
			}

			int textOffSet = element.getTextOffset();
			editor.getCaretModel().moveToOffset(textOffSet);

			element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
			final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(element);
			builder.replaceRange(TextRange.create(text.length() - 1, text.length() - 1), PyNames.OBJECT);
			Template template = builder.buildInlineTemplate();
			TemplateManager.getInstance(project).startTemplate(editor, template);
		}
	}
}