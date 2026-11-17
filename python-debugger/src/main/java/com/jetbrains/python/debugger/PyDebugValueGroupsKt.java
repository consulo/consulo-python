// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import consulo.application.Application;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XNamedValue;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Java counterpart of the top-level declarations in JB {@code PyDebugValueGroups.kt}.
 */
public final class PyDebugValueGroupsKt {
    public static final String DUNDER_LEN = "__len__";
    public static final String DUNDER_EX = "__exception__";
    public static final Set<String> PROTECTED_ATTRS_EXCLUDED = Set.of(DUNDER_LEN, DUNDER_EX);

    private PyDebugValueGroupsKt() {
    }

    public static Set<String> getPROTECTED_ATTRS_EXCLUDED() {
        return PROTECTED_ATTRS_EXCLUDED;
    }

    public static void extractChildrenToGroup(LocalizeValue groupName,
                                              Image icon,
                                              XCompositeNode node,
                                              XValueChildrenList children,
                                              Predicate<String> predicate,
                                              Set<String> excludedNames) {
        extractChildrenToGroup(groupName, icon, node, children, predicate, excludedNames, false);
    }

    /**
     * @param alreadySorted whether the caller owns the order of {@code children}, so that the platform does not sort
     *                      the children of the group again. See {@link #addGroupValues}.
     */
    public static void extractChildrenToGroup(LocalizeValue groupName,
                                              Image icon,
                                              XCompositeNode node,
                                              XValueChildrenList children,
                                              Predicate<String> predicate,
                                              Set<String> excludedNames,
                                              boolean alreadySorted) {
        FilterResult filterResult = filterChildren(children, predicate, excludedNames);
        node.addChildren(filterResult.filteredChildren, filterResult.groupElements.isEmpty());
        addGroupValues(groupName, icon, node, filterResult.groupElements, null, ProcessDebugger.GROUP_TYPE.DEFAULT, null, alreadySorted);
    }

    private static class FilterResult {
        final XValueChildrenList filteredChildren = new XValueChildrenList();
        final Map<String, XValue> groupElements = new LinkedHashMap<>();
    }

    private static FilterResult filterChildren(XValueChildrenList children, Predicate<String> predicate, Set<String> excludedNames) {
        FilterResult result = new FilterResult();
        for (int i = 0; i < children.size(); i++) {
            XValue value = children.getValue(i);
            String name = children.getName(i);
            if (value instanceof XNamedValue namedValue) {
                if (predicate.test(namedValue.getName()) && !excludedNames.contains(namedValue.getName())) {
                    result.groupElements.put(name, value);
                }
                else {
                    result.filteredChildren.add(name, value);
                }
            }
        }
        return result;
    }

    public static void addGroupValues(LocalizeValue groupName,
                                      Image groupIcon,
                                      XCompositeNode node,
                                      @Nullable Map<String, XValue> groupElements,
                                      @Nullable PyFrameAccessor myDebugProcess,
                                      ProcessDebugger.GROUP_TYPE groupType,
                                      @Nullable String nameSuffix) {
        addGroupValues(groupName, groupIcon, node, groupElements, myDebugProcess, groupType, nameSuffix, false);
    }

    /**
     * @param alreadySorted whether the caller owns the order of {@code groupElements}, so that the platform does not
     *                      sort the children of the group again. A group is a node of its own, so the flag the
     *                      parent node carries does not reach it.
     */
    public static void addGroupValues(LocalizeValue groupName,
                                      Image groupIcon,
                                      XCompositeNode node,
                                      @Nullable Map<String, XValue> groupElements,
                                      @Nullable PyFrameAccessor myDebugProcess,
                                      ProcessDebugger.GROUP_TYPE groupType,
                                      @Nullable String nameSuffix,
                                      boolean alreadySorted) {
        PyXValueGroup group = new PyXValueGroup(groupName, groupType) {
            @Override
            public void computeChildren(XCompositeNode node) {
                if (node.isObsolete()) {
                    return;
                }
                node.setAlreadySorted(alreadySorted);
                Application.get().executeOnPooledThread(() -> {
                    XValueChildrenList list;
                    if (groupType == ProcessDebugger.GROUP_TYPE.DEFAULT) {
                        list = getDefaultGroupNodes(groupElements, nameSuffix);
                    }
                    else {
                        list = myDebugProcess != null ? getSpecialGroupNodes(myDebugProcess, nameSuffix, groupType) : null;
                    }
                    if (list != null) {
                        node.addChildren(list, true);
                    }
                });
            }

            @Override
            public Image getIcon() {
                return groupIcon;
            }
        };
        node.addChildren(XValueChildrenList.bottomGroup(group), true);
    }

    private static XValueChildrenList getDefaultGroupNodes(@Nullable Map<String, XValue> groupElements, @Nullable String nameSuffix) {
        XValueChildrenList list = new XValueChildrenList();
        if (groupElements != null) {
            for (Map.Entry<String, XValue> entry : groupElements.entrySet()) {
                String name = addSuffix(entry.getKey(), nameSuffix);
                list.add(name, entry.getValue());
            }
        }
        return list;
    }

    private static XValueChildrenList getSpecialGroupNodes(PyFrameAccessor myDebugProcess,
                                                           @Nullable String nameSuffix,
                                                           ProcessDebugger.GROUP_TYPE groupType) {
        XValueChildrenList list;
        try {
            list = myDebugProcess.loadSpecialVariables(groupType);
        }
        catch (PyDebuggerException e) {
            // Kotlin does not have checked exceptions, the JB code lets the exception escape the pooled task
            throw new RuntimeException(e);
        }
        return addSuffix(list != null ? list : new XValueChildrenList(), nameSuffix);
    }

    private static XValueChildrenList addSuffix(XValueChildrenList list, @Nullable String nameSuffix) {
        if (nameSuffix != null) {
            XValueChildrenList result = new XValueChildrenList();
            for (int i = 0; i < list.size(); i++) {
                XValue value = list.getValue(i);
                String name = list.getName(i);
                result.add(addSuffix(name, nameSuffix), value);
            }
            return result;
        }
        return list;
    }

    private static String addSuffix(String name, @Nullable String nameSuffix) {
        return nameSuffix == null ? name : name + nameSuffix;
    }
}
