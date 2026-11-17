// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.actions;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.render.DecimalRenderer;
import com.jetbrains.python.debugger.render.PyNodeRenderer;
import com.jetbrains.python.debugger.render.PyNodeRendererManager;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.frame.XValuePlace;
import consulo.execution.debug.ui.XValueTree;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ActionImpl(
    id = "PyDebugger.ViewAsGroup",
    parents = @ActionParentRef(value = @ActionRef(id = "XDebugger.ValueGroup"), anchor = ActionRefAnchor.LAST)
)
public class ViewAsGroup extends ActionGroup implements DumbAware {
    private static final AnAction[] myChildren;

    static {
        List<AnAction> children = new ArrayList<>();
        for (PyNodeRenderer renderer : PyNodeRendererManager.getInstance().getAvailableRenderers()) {
            children.add(new RendererAction(renderer));
        }
        myChildren = children.toArray(EMPTY_ARRAY);
    }

    private static final class RendererAction extends ToggleAction {
        private final PyNodeRenderer myPyNodeRenderer;

        private RendererAction(PyNodeRenderer pyNodeRenderer) {
            super(LocalizeValue.of(pyNodeRenderer.getName()));
            myPyNodeRenderer = pyNodeRenderer;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            List<PyDebugValue> values = getSelectedValues(e);

            if (values.isEmpty()) {
                return false;
            }

            if (values.size() == 1) {
                PyNodeRenderer renderer = values.get(0).getDescriptor().getRenderer();
                return renderer == null ? myPyNodeRenderer instanceof DecimalRenderer : renderer == myPyNodeRenderer;
            }

            boolean allRenderersAreTheSame = values.stream().map(value -> value.getDescriptor().getRenderer()).distinct().count() == 1;

            if (allRenderersAreTheSame) {
                PyNodeRenderer renderer = values.get(0).getDescriptor().getRenderer();
                return renderer == myPyNodeRenderer || (renderer == null && myPyNodeRenderer instanceof DecimalRenderer);
            }

            return false;
        }

        @Override
        @RequiredUIAccess
        public void setSelected(AnActionEvent e, boolean state) {
            if (!state) {
                return;
            }

            for (XValueNode node : getSelectedNodes(e)) {
                if (!(node.getValueContainer() instanceof PyDebugValue value)) {
                    continue;
                }
                value.getDescriptor().setRenderer(myPyNodeRenderer);
                if (value.getValue() == null) {
                    continue;
                }
                // the presentation applies the renderer of the descriptor
                value.computePresentation(node, XValuePlace.TREE);
            }
        }

        public PyNodeRenderer getRenderer() {
            return myPyNodeRenderer;
        }
    }

    public ViewAsGroup() {
        super(PyLocalize.groupPydebuggerViewasgroupText(), true);
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
        return true;
    }

    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        if (e == null) {
            return EMPTY_ARRAY;
        }

        List<PyDebugValue> values = getSelectedValues(e);

        if (values.isEmpty()) {
            return EMPTY_ARRAY;
        }

        for (PyDebugValue value : values) {
            for (AnAction child : myChildren) {
                if (!((RendererAction) child).getRenderer().isApplicable(value.getType())) {
                    return EMPTY_ARRAY;
                }
            }
        }

        return myChildren;
    }

    private static List<XValueNode> getSelectedNodes(AnActionEvent e) {
        XValueTree tree = e.getData(XValueTree.KEY);
        return tree == null ? List.of() : tree.getSelectedNodes();
    }

    public static List<PyDebugValue> getSelectedValues(AnActionEvent e) {
        List<PyDebugValue> values = new ArrayList<>();
        for (XValueNode node : getSelectedNodes(e)) {
            if (node.getValueContainer() instanceof PyDebugValue value) {
                values.add(value);
            }
        }
        return values;
    }
}
