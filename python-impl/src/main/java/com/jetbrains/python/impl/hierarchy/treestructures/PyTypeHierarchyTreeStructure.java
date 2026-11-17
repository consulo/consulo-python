// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.treestructures;

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;

public class PyTypeHierarchyTreeStructure extends PySubTypesHierarchyTreeStructure {
    public PyTypeHierarchyTreeStructure(PyClass cl) {
        super(cl.getProject(), buildHierarchyElement(cl));
        setBaseElement(myBaseDescriptor);
    }

    private static PyHierarchyNodeDescriptor buildHierarchyElement(PyClass cl) {
        PyHierarchyNodeDescriptor descriptor = null;
        TypeEvalContext context = TypeEvalContext.codeAnalysis(cl.getProject(), cl.getContainingFile());
        for (PyClass superClass : Lists.reverse(cl.getAncestorClasses(context))) {
            PyHierarchyNodeDescriptor newDescriptor = new PyHierarchyNodeDescriptor(descriptor, superClass, false);
            if (descriptor != null) {
                descriptor.setCachedChildren(new PyHierarchyNodeDescriptor[]{newDescriptor});
            }
            descriptor = newDescriptor;
        }
        PyHierarchyNodeDescriptor newDescriptor = new PyHierarchyNodeDescriptor(descriptor, cl, true);
        if (descriptor != null) {
            descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
        }
        return newDescriptor;
    }
}
