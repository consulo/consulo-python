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
package com.jetbrains.python.impl.hierarchy.treestructures;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 12, 2009
 * Time: 7:04:07 PM
 */
public class PySuperTypesHierarchyTreeStructure extends HierarchyTreeStructure
{
	public PySuperTypesHierarchyTreeStructure(@Nonnull PyClass cl)
	{
		super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
	}

	@Nonnull
	protected Object[] buildChildren(@Nonnull HierarchyNodeDescriptor descriptor)
	{
		List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
		if(descriptor instanceof PyHierarchyNodeDescriptor)
		{
			PyHierarchyNodeDescriptor pyDescriptor = (PyHierarchyNodeDescriptor) descriptor;
			PsiElement element = pyDescriptor.getPsiElement();
			if(element instanceof PyClass)
			{
				PyClass cls = (PyClass) element;
				PyClass[] superClasses = cls.getSuperClasses(null);
				for(PyClass superClass : superClasses)
				{
					res.add(new PyHierarchyNodeDescriptor(descriptor, superClass, false));
				}
			}
		}
		return res.toArray();
	}
}
