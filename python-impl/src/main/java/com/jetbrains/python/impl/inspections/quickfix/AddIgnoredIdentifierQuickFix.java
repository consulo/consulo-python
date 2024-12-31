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

import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspectionState;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class AddIgnoredIdentifierQuickFix implements LocalQuickFix, LowPriorityAction
{
	public static final String END_WILDCARD = ".*";

	@Nonnull
	private final QualifiedName myIdentifier;
	private final boolean myIgnoreAllAttributes;

	public AddIgnoredIdentifierQuickFix(@Nonnull QualifiedName identifier, boolean ignoreAllAttributes)
	{
		myIdentifier = identifier;
		myIgnoreAllAttributes = ignoreAllAttributes;
	}

	@Nonnull
	@Override
	public String getName()
	{
		if(myIgnoreAllAttributes)
		{
			return "Mark all unresolved attributes of '" + myIdentifier + "' as ignored";
		}
		else
		{
			return "Ignore unresolved reference '" + myIdentifier + "'";
		}
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return "Ignore unresolved reference";
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement context = descriptor.getPsiElement();
		InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
		profile.<PyUnresolvedReferencesInspection, PyUnresolvedReferencesInspectionState>modifyToolSettings(PyUnresolvedReferencesInspection.class.getSimpleName(), context, (i, s) ->
		{
			String name = myIdentifier.toString();
			if(myIgnoreAllAttributes)
			{
				name += END_WILDCARD;
			}
			if(!s.ignoredIdentifiers.contains(name))
			{
				s.ignoredIdentifiers.add(name);
			}
		});
	}
}
