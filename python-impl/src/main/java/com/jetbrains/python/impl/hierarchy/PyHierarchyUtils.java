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
package com.jetbrains.python.impl.hierarchy;

import consulo.ide.impl.idea.ide.hierarchy.HierarchyBrowserManager;
import consulo.ui.ex.tree.AlphaComparator;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.project.Project;

import java.util.Comparator;

/**
 * @author Alexey.Ivanov
 * @since 2009-08-12
 */
public class PyHierarchyUtils {
    private static final Comparator<NodeDescriptor> NODE_DESCRIPTOR_COMPARATOR =
        (first, second) -> first.getIndex() - second.getIndex();

    private PyHierarchyUtils() {
    }

    public static Comparator<NodeDescriptor> getComparator(Project project) {
        if (HierarchyBrowserManager.getInstance(project).getState().SORT_ALPHABETICALLY) {
            return AlphaComparator.INSTANCE;
        }
        else {
            return NODE_DESCRIPTOR_COMPARATOR;
        }
    }
}
