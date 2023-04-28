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
package com.jetbrains.python.hierarchy.treestructures;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.application.util.query.Query;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 12, 2009
 * Time: 7:03:13 PM
 */
public class PySubTypesHierarchyTreeStructure extends HierarchyTreeStructure
{
	protected PySubTypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor)
	{
		super(project, baseDescriptor);
	}

	public PySubTypesHierarchyTreeStructure(@Nonnull final PyClass cl)
	{
		super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
	}

	@Nonnull
	protected Object[] buildChildren(@Nonnull HierarchyNodeDescriptor descriptor)
	{
		final List<PyHierarchyNodeDescriptor> res = new ArrayList<PyHierarchyNodeDescriptor>();
		final PsiElement element = ((PyHierarchyNodeDescriptor) descriptor).getPsiElement();
		if(element instanceof PyClass)
		{
			final PyClass cls = (PyClass) element;
			Query<PyClass> subClasses = PyClassInheritorsSearch.search(cls, false);
			for(PyClass subClass : subClasses)
			{
				res.add(new PyHierarchyNodeDescriptor(descriptor, subClass, false));
			}

		}

		return ArrayUtil.toObjectArray(res);
	}
}
