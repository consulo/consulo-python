// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.codeinsight.PyTypeNameResolver;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.PyClass;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.execution.debug.frame.XValueChildrenList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Top-level functions of JetBrains' ConfigureTypeRenderersAction.kt.
 * The classes of that file are {@link ConfigureTypeRenderersAction} and {@link ConfigureTypeRenderersHyperLink}.
 */
public final class ConfigureTypeRenderersActionKt {
    private ConfigureTypeRenderersActionKt() {
    }

    public static @Nullable Integer getRendererIndexWithId(String typeRendererId) {
        PyUserTypeRenderersSettings settings = PyUserTypeRenderersSettings.getInstance();
        List<PyUserNodeRenderer> renderers = settings.getRenderers();
        for (int index = 0; index < renderers.size(); index++) {
            if (renderers.get(index).isApplicable() && renderers.get(index).getToType().equals(typeRendererId)) {
                return index;
            }
        }
        return null;
    }

    public static @Nullable PyUserNodeRenderer getTypeRenderer(PyDebugValue debugValue) {
        String typeRendererId = debugValue.getTypeRendererId();
        if (typeRendererId == null) {
            return null;
        }
        Integer index = getRendererIndexWithId(typeRendererId);
        if (index == null) {
            return null;
        }
        return PyUserTypeRenderersSettings.getInstance().getRenderers().get(index);
    }

    private static List<PyDebugValue> loadCustomChildren(PyFrameAccessor frameAccessor,
                                                         PyUserNodeRenderer renderer,
                                                         PyDebugValue debugValue) throws PyDebuggerException {
        List<PyDebugValue> customChildren = new ArrayList<>();
        for (PyUserNodeRenderer.ChildInfo child : renderer.getChildrenRenderer().getChildren()) {
            String res = child.getExpression().replace("self", debugValue.getEvaluationExpression());
            PyDebugValue evalResult = frameAccessor.evaluate(res, false, true);
            customChildren.add(evalResult);
        }
        return customChildren;
    }

    public static XValueChildrenList loadTypeRendererChildren(PyFrameAccessor frameAccessor,
                                                              PyDebugValue debugValue,
                                                              PyUserNodeRenderer typeRenderer) throws PyDebuggerException {
        XValueChildrenList childrenList = new XValueChildrenList();
        if (!typeRenderer.getChildrenRenderer().isDefault()) {
            List<PyDebugValue> customChildren = loadCustomChildren(frameAccessor, typeRenderer, debugValue);
            for (PyDebugValue child : customChildren) {
                childrenList.add(child.getName(), child);
            }
        }
        if (typeRenderer.getChildrenRenderer().isDefault() ||
            typeRenderer.getChildrenRenderer().getAppendDefaultChildren()) {
            XValueChildrenList defaultChildren = frameAccessor.loadVariableDefaultView(debugValue);
            if (defaultChildren != null) {
                for (int i = 0; i < defaultChildren.size(); i++) {
                    childrenList.add(defaultChildren.getName(i), defaultChildren.getValue(i));
                }
            }
        }
        return childrenList;
    }

    static void showSettingsWithNewRenderer(Project project, PyDebugValue debugValue) {
        ShowSettingsUtil.getInstance().showAndSelect(project, PyUserTypeRenderersConfigurable.class, it -> {
            PyUserNodeRenderer newRenderer = new PyUserNodeRenderer(true, it.getCurrentlyVisibleNames());
            String qualifiedType = debugValue.getQualifiedType();
            newRenderer.setName(debugValue.getType() + " " + PyLocalize.formDebuggerVariablesViewUserTypeRenderersRenderer().get());

            if (qualifiedType == null) {
                it.setNewRendererToAdd(newRenderer);
                return;
            }

            PyClass pyClass = new PyTypeNameResolver(project).resolve(qualifiedType);
            if (pyClass != null) {
                newRenderer.setToType(qualifiedType);
            }
            else {
                Collection<PyClass> classes = PyClassNameIndex.find(debugValue.getType(), project, GlobalSearchScope.projectScope(project));
                boolean resolveFound = false;
                if (!classes.isEmpty()) {
                    QualifiedName canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(classes.iterator().next(), null);
                    if (canonicalImportPath != null) {
                        newRenderer.setToType(canonicalImportPath + "." + debugValue.getType());
                        resolveFound = true;
                    }
                }
                if (!resolveFound) {
                    // Show type even if we failed to resolve it
                    newRenderer.setToType(debugValue.getQualifiedType() != null ? debugValue.getQualifiedType() : "");
                    newRenderer.setTypeCanonicalImportPath(debugValue.getQualifiedType() != null ? debugValue.getQualifiedType() : "");
                    newRenderer.setTypeQualifiedName(debugValue.getQualifiedType() != null ? debugValue.getQualifiedType() : "");
                }
            }

            it.setNewRendererToAdd(newRenderer);
        });
    }

    static void showSettingsWithSelectedRenderer(Project project, int rendererIndexToSelect) {
        ShowSettingsUtil.getInstance().showAndSelect(project, PyUserTypeRenderersConfigurable.class,
            it -> it.setRendererIndexToSelect(rendererIndexToSelect));
    }
}
