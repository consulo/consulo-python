// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.call;

import com.jetbrains.python.impl.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.editor.hierarchy.HierarchyScope;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author novokrest
 */
public abstract class PyCallHierarchyTreeStructureBase extends HierarchyTreeStructure {
    private final @Nullable HierarchyScope myScopeType;

    public PyCallHierarchyTreeStructureBase(Project project, PsiElement element, @Nullable HierarchyScope currentScopeType) {
        super(project, new PyHierarchyNodeDescriptor(null, element, true));
        myScopeType = currentScopeType;
    }

    protected abstract Map<PsiElement, Collection<PsiElement>> getChildren(PyElement element);

    @Override
    protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        List<PyHierarchyNodeDescriptor> descriptors = new ArrayList<>();
        if (descriptor instanceof PyHierarchyNodeDescriptor pyDescriptor) {
            PsiElement element = pyDescriptor.getPsiElement();
            boolean isCallable = element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
            HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
            if (!(element instanceof PyElement) || !isCallable || nodeDescriptor == null) {
                return ArrayUtil.EMPTY_OBJECT_ARRAY;
            }

            Map<PsiElement, Collection<PsiElement>> children = getChildren((PyElement) element);

            Map<PsiElement, PyHierarchyNodeDescriptor> callerToDescriptorMap = new HashMap<>();
            PsiElement baseClass = element instanceof PyFunction ? ((PyFunction) element).getContainingClass() : null;

            children.forEach((caller, usages) -> {
                // Consulo: no scope means everything is in scope
                if (myScopeType == null || isInScope(baseClass, caller, myScopeType)) {
                    PyHierarchyNodeDescriptor callerDescriptor = callerToDescriptorMap.get(caller);
                    if (callerDescriptor == null) {
                        callerDescriptor = new PyHierarchyNodeDescriptor(descriptor, caller, usages, false);
                        callerToDescriptorMap.put(caller, callerDescriptor);
                        descriptors.add(callerDescriptor);
                    }
                }
            });
        }
        return ArrayUtil.toObjectArray(descriptors);
    }
}
