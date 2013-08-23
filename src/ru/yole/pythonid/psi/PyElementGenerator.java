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

package ru.yole.pythonid.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract interface PyElementGenerator
{
  public abstract ASTNode createNameIdentifier(Project paramProject, String paramString);

  public abstract PyStringLiteralExpression createStringLiteralAlreadyEscaped(Project paramProject, String paramString);

  public abstract PyStringLiteralExpression createStringLiteralFromString(Project paramProject, @NotNull PsiFile paramPsiFile, String paramString);

  public abstract PyListLiteralExpression createListLiteral(Project paramProject);

  public abstract PyKeywordArgument createKeywordArgument(Project paramProject, String paramString, @Nullable PyExpression paramPyExpression);

  public abstract ASTNode createComma(Project paramProject);

  public abstract PsiElement insertItemIntoList(Project paramProject, PyElement paramPyElement, @Nullable PyExpression paramPyExpression1, PyExpression paramPyExpression2)
    throws IncorrectOperationException;

  public abstract PyBinaryExpression createBinaryExpression(Project paramProject, String paramString, PyExpression paramPyExpression1, PyExpression paramPyExpression2);

  public abstract PyCallExpression createCallExpression(Project paramProject, String paramString);

  public abstract PyExpressionStatement createExpressionStatement(Project paramProject, PyExpression paramPyExpression);

  public abstract void setStringValue(PyStringLiteralExpression paramPyStringLiteralExpression, String paramString);
}