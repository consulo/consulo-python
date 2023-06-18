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

package com.jetbrains.python.impl.inspections;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * Warns about shadowing built-in names.
 *
 * @author vlan
 */
@ExtensionImpl
public class PyShadowingBuiltinsInspection extends PyInspection
{
	@Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new PyShadowingBuiltinsInspectionState();
	}

	@Nonnull
	@Override
	public String getDisplayName()
	{
		return "Shadowing built-ins";
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		PyShadowingBuiltinsInspectionState inspectionState = (PyShadowingBuiltinsInspectionState) state;
		return new Visitor(holder, session, inspectionState.ignoredNames);
	}

	private static class Visitor extends PyInspectionVisitor
	{
		private final Set<String> myIgnoredNames;

		public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session, @Nonnull Collection<String> ignoredNames)
		{
			super(holder, session);
			myIgnoredNames = ImmutableSet.copyOf(ignoredNames);
		}

		@Override
		public void visitPyClass(@Nonnull PyClass node)
		{
			processElement(node);
		}

		@Override
		public void visitPyFunction(@Nonnull PyFunction node)
		{
			processElement(node);
		}

		@Override
		public void visitPyNamedParameter(@Nonnull PyNamedParameter node)
		{
			processElement(node);
		}

		@Override
		public void visitPyTargetExpression(@Nonnull PyTargetExpression node)
		{
			if(node.getQualifier() == null)
			{
				processElement(node);
			}
		}

		private void processElement(@Nonnull PsiNameIdentifierOwner element)
		{
			final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
			if(owner instanceof PyClass)
			{
				return;
			}
			final String name = element.getName();
			if(name != null && !myIgnoredNames.contains(name))
			{
				final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
				final PsiElement builtin = builtinCache.getByName(name);
				if(builtin != null && !PyUtil.inSameFile(builtin, element))
				{
					final PsiElement identifier = element.getNameIdentifier();
					final PsiElement problemElement = identifier != null ? identifier : element;
					registerProblem(problemElement, String.format("Shadows built-in name '%s'", name),
							ProblemHighlightType.WEAK_WARNING, null, new PyRenameElementQuickFix(), new PyIgnoreBuiltinQuickFix(name));
				}
			}
		}

		private static class PyIgnoreBuiltinQuickFix implements LocalQuickFix, LowPriorityAction
		{
			@Nonnull
			private final String myName;

			private PyIgnoreBuiltinQuickFix(@Nonnull String name)
			{
				myName = name;
			}

			@Nonnull
			@Override
			public String getName()
			{
				return getFamilyName() + " \"" + myName + "\"";
			}

			@Nonnull
			@Override
			public String getFamilyName()
			{
				return "Ignore shadowed built-in name";
			}

			@Override
			public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
			{
				final PsiElement element = descriptor.getPsiElement();
				if(element != null)
				{
					final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
					profile.<PyShadowingBuiltinsInspection, PyShadowingBuiltinsInspectionState>modifyToolSettings(PyShadowingBuiltinsInspection.class.getSimpleName(), element, (i, s) ->
					{
						if(!s.ignoredNames.contains(myName))
						{
							s.ignoredNames.add(myName);
						}
					});
				}
			}
		}
	}
}

