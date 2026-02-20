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

import java.util.Comparator;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.JPanel;
import javax.swing.JTree;

import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.TypeHierarchyBrowserBase;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.logging.Logger;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.hierarchy.treestructures.PySubTypesHierarchyTreeStructure;
import com.jetbrains.python.impl.hierarchy.treestructures.PySuperTypesHierarchyTreeStructure;
import com.jetbrains.python.impl.hierarchy.treestructures.PyTypeHierarchyTreeStructure;
import com.jetbrains.python.psi.PyClass;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 31, 2009
 * Time: 6:14:42 PM
 */
public class PyTypeHierarchyBrowser extends TypeHierarchyBrowserBase
{
	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.hierarchy.TypeHierarchyBrowser");

	protected PyTypeHierarchyBrowser(@Nonnull PyClass pyClass)
	{
		super(pyClass.getProject(), pyClass);
	}

	@Nullable
	protected PsiElement getElementFromDescriptor(@Nonnull HierarchyNodeDescriptor descriptor)
	{
		if(!(descriptor instanceof PyHierarchyNodeDescriptor))
		{
			return null;
		}
		return descriptor.getPsiElement();
	}

	protected void createTrees(@Nonnull Map<String, JTree> trees)
	{
		createTreeAndSetupCommonActions(trees, "PyTypeHierarchyPopupMenu");
	}

	@Nullable
	protected JPanel createLegendPanel()
	{
		return null;
	}

	protected boolean isApplicableElement(@Nonnull PsiElement element)
	{
		return (element instanceof PyClass);
	}

	@Nullable
	protected consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure createHierarchyTreeStructure(@Nonnull String typeName, @Nonnull PsiElement psiElement)
	{
		if(SUPERTYPES_HIERARCHY_TYPE.equals(typeName))
		{
			return new PySuperTypesHierarchyTreeStructure((PyClass) psiElement);
		}
		else if(SUBTYPES_HIERARCHY_TYPE.equals(typeName))
		{
			return new PySubTypesHierarchyTreeStructure((PyClass) psiElement);
		}
		else if(TYPE_HIERARCHY_TYPE.equals(typeName))
		{
			return new PyTypeHierarchyTreeStructure((PyClass) psiElement);
		}
		else
		{
			LOG.error("unexpected type: " + typeName);
			return null;
		}
	}

	@Nullable
	protected Comparator<NodeDescriptor> getComparator()
	{
		return PyHierarchyUtils.getComparator(myProject);
	}

	protected boolean isInterface(PsiElement psiElement)
	{
		return false;
	}

	protected boolean canBeDeleted(PsiElement psiElement)
	{
		return (psiElement instanceof PyClass);
	}

	@Nonnull
	protected String getQualifiedName(PsiElement psiElement)
	{
		if(psiElement instanceof PyClass)
		{
			String name = ((PyClass) psiElement).getName();
			if(name != null)
			{
				return name;
			}
		}
		return "";
	}
}
