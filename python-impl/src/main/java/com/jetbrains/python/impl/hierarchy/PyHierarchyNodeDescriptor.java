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
package com.jetbrains.python.impl.hierarchy;

import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.navigation.ItemPresentation;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.util.lang.Comparing;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 31, 2009
 * Time: 6:26:37 PM
 */
public class PyHierarchyNodeDescriptor extends consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor
{
	public PyHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor, @Nonnull final PsiElement element, final boolean isBase)
	{
		super(element.getProject(), parentDescriptor, element, isBase);
	}

	@RequiredUIAccess
	@Override
	public boolean update()
	{
		boolean changes = super.update();
		final CompositeAppearance oldText = myHighlightedText;

		myHighlightedText = new CompositeAppearance();

		NavigatablePsiElement element = (NavigatablePsiElement) getPsiElement();
		if(element == null)
		{
			final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
			if(!myHighlightedText.getText().startsWith(invalidPrefix))
			{
				myHighlightedText.getBeginning().addText(invalidPrefix, consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor.getInvalidPrefixAttributes());
			}
			return true;
		}

		final ItemPresentation presentation = element.getPresentation();
		if(presentation != null)
		{
			if(element instanceof PyFunction)
			{
				final PyClass cls = ((PyFunction) element).getContainingClass();
				if(cls != null)
				{
					myHighlightedText.getEnding().addText(cls.getName() + ".");
				}
			}
			myHighlightedText.getEnding().addText(presentation.getPresentableText());
			myHighlightedText.getEnding().addText(" " + presentation.getLocationString(), consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor.getPackageNameAttributes());
		}
		myName = myHighlightedText.getText();

		if(!Comparing.equal(myHighlightedText, oldText))
		{
			changes = true;
		}
		return changes;
	}
}
