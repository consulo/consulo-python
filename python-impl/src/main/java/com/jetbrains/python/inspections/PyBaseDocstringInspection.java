/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.PythonUnitTestUtil;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseDocstringInspection extends PyInspection
{
	@Nonnull
	@Override
	public abstract Visitor buildVisitor(@Nonnull ProblemsHolder holder, boolean isOnTheFly, @Nonnull LocalInspectionToolSession session);

	protected static abstract class Visitor extends PyInspectionVisitor
	{
		public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session)
		{
			super(holder, session);
		}

		@Override
		public final void visitPyFile(@Nonnull PyFile node)
		{
			checkDocString(node);
		}

		@Override
		public final void visitPyFunction(@Nonnull PyFunction node)
		{
			if(PythonUnitTestUtil.isUnitTestCaseFunction(node))
			{
				return;
			}
			final PyClass containingClass = node.getContainingClass();
			if(containingClass != null && PythonUnitTestUtil.isUnitTestCaseClass(containingClass))
			{
				return;
			}
			final Property property = node.getProperty();
			if(property != null && (node == property.getSetter().valueOrNull() || node == property.getDeleter().valueOrNull()))
			{
				return;
			}
			final String name = node.getName();
			if(name != null && !name.startsWith("_"))
			{
				checkDocString(node);
			}
		}

		@Override
		public final void visitPyClass(@Nonnull PyClass node)
		{
			if(PythonUnitTestUtil.isUnitTestCaseClass(node))
			{
				return;
			}
			final String name = node.getName();
			if(name == null || name.startsWith("_"))
			{
				return;
			}
			checkDocString(node);
		}

		protected abstract void checkDocString(@Nonnull PyDocStringOwner node);
	}
}
