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
package com.jetbrains.python.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;

/**
 * @author Mikhail Golubev
 */
public class PyTypeDeclarationStatementImpl extends PyElementImpl implements PyTypeDeclarationStatement
{
	public PyTypeDeclarationStatementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Nonnull
	@Override
	public PyExpression getTarget()
	{
		return findNotNullChildByClass(PyExpression.class);
	}

	@Nullable
	@Override
	public PyAnnotation getAnnotation()
	{
		return findChildByClass(PyAnnotation.class);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyTypeDeclarationStatement(this);
	}
}
