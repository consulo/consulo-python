// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.AbstractPolicy;
import com.jetbrains.python.debugger.PolicyListener;
import com.jetbrains.python.debugger.QuotingPolicy;
import com.jetbrains.python.debugger.SortingPolicy;
import com.jetbrains.python.debugger.ValuesPolicy;

import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XDebuggerTreeNodeHyperlink;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import org.jspecify.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class PyVariableViewSettings {
    /**
     * Fills the debugger toolwindow's "Debugger Settings" gear popup with the actions shared by pydevd and debugpy.
     * {@code supportsSyncVariablesLoading} controls the only structural difference between the two: debugpy always
     * loads variables asynchronously, so it has no "Synchronously" option in {@link VariablesPolicyGroup}.
     */
    public static void registerSettingsActions(DefaultActionGroup settings,
                                                XDebugProcess process,
                                                WatchReturnValuesAction watchReturnValuesAction,
                                                boolean supportsSyncVariablesLoading) {
        settings.removeAll();
        settings.add(ActionManager.getInstance().getAction("XDebugger.Inline"));
        settings.add(ActionManager.getInstance().getAction("XDebugger.UnmuteOnStop"));
        settings.add(watchReturnValuesAction);
        settings.add(new SimplifiedView(process));
        settings.add(new VariablesPolicyGroup(supportsSyncVariablesLoading));
        settings.add(new QuotingPolicyGroup());
    }

    public static final class SimplifiedView extends ToggleAction {
        private final XDebugProcess myProcess;
        private volatile boolean mySimplifiedView;

        public SimplifiedView(@Nullable XDebugProcess debugProcess) {
            super(PyLocalize.debuggerSimplifiedViewText().get(), PyLocalize.debuggerSimplifiedViewDescription().get(), null);
            mySimplifiedView = PyDebuggerSettings.getInstance().isSimplifiedView();
            myProcess = debugProcess;
        }

        @Override
        public void update(AnActionEvent e) {
            super.update(e);
            Presentation presentation = e.getPresentation();
            presentation.setEnabled(true);
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return mySimplifiedView;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean hide) {
            mySimplifiedView = hide;
            PyDebuggerSettings.getInstance().setSimplifiedView(hide);
            if (myProcess != null) {
                myProcess.getSession().rebuildViews();
            }
        }
    }

    public static void showWarningMessage(@Nullable XCompositeNode node) {
        if (node == null) {
            return;
        }
        PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        if (debuggerSettings.getValuesPolicy() == ValuesPolicy.ON_DEMAND) {
            return;
        }

        node.setMessage(
          PyLocalize.debuggerVariablesViewWarningMessage(),
          PlatformIconGroup.generalBalloonwarning(),
          SimpleTextAttributes.REGULAR_ATTRIBUTES,
          new XDebuggerTreeNodeHyperlink(PyLocalize.debuggerVariablesViewSwitchToLoadingOnDemand()) {
              private boolean linkClicked = false;

              @Override
              public void onClick(MouseEvent event) {
                  debuggerSettings.setValuesPolicy(ValuesPolicy.ON_DEMAND);
                  linkClicked = true;
              }

              @Override
              public LocalizeValue getLinkText() {
                  if (linkClicked) {
                      return LocalizeValue.empty();
                  }
                  else {
                      return PyLocalize.debuggerVariablesViewSwitchToLoadingOnDemand();
                  }
              }
          });
    }

    public static class VariablePolicyAction extends AbstractPolicyAction<ValuesPolicy, VariablesPolicyGroup> {

        public VariablePolicyAction(String text,
                                    String description,
                                    ValuesPolicy policy,
                                    VariablesPolicyGroup actionGroup) {
            super(text, description, policy, actionGroup);
        }

        @Override
        protected void changeDebuggerSettings() {
            PyDebuggerSettings.getInstance().setValuesPolicy(getPolicy());
        }
    }

    public static final class VariablesPolicyGroup extends AbstractPolicyGroup<ValuesPolicy, VariablePolicyAction> {
        public VariablesPolicyGroup() {
            this(true);
        }

        /**
         * @param supportsSyncLoading whether to offer the "Synchronously" option; debugpy always loads variables
         *                             asynchronously, so it never offers this option.
         */
        public VariablesPolicyGroup(boolean supportsSyncLoading) {
            super(PyLocalize.debuggerVariablesLoadingPolicy().get());
            List<VariablePolicyAction> actions = new ArrayList<>();
            if (supportsSyncLoading) {
                actions.add(new VariablePolicyAction(PyLocalize.debuggerVariablesLoadingSynchronouslyText().get(),
                                                      PyLocalize.debuggerVariablesLoadingSynchronouslyDescription().get(),
                                                      ValuesPolicy.SYNC, this));
            }
            actions.add(new VariablePolicyAction(PyLocalize.debuggerVariablesLoadingAsynchronouslyText().get(),
                                                  PyLocalize.debuggerVariablesLoadingAsynchronouslyDescription().get(),
                                                  ValuesPolicy.ASYNC, this));
            actions.add(new VariablePolicyAction(PyLocalize.debuggerVariablesLoadingOnDemandText().get(),
                                                  PyLocalize.debuggerVariablesLoadingOnDemandDescription().get(),
                                                  ValuesPolicy.ON_DEMAND, this));
            addPolicyActions(actions.toArray(new VariablePolicyAction[0]));
        }

        @Override
        protected ValuesPolicy getDebuggerPolicy() {
            return PyDebuggerSettings.getInstance().getValuesPolicy();
        }
    }

    public static final class QuotingPolicyAction extends AbstractPolicyAction<QuotingPolicy, QuotingPolicyGroup> {

        public QuotingPolicyAction(String text,
                                   String description,
                                   QuotingPolicy policy,
                                   QuotingPolicyGroup actionGroup) {
            super(text, description, policy, actionGroup);
        }

        @Override
        protected void changeDebuggerSettings() {
            PyDebuggerSettings.getInstance().setQuotingPolicy(getPolicy());
        }
    }

    public static final class QuotingPolicyGroup extends AbstractPolicyGroup<QuotingPolicy, QuotingPolicyAction> {
        public QuotingPolicyGroup() {
            super(PyLocalize.debuggerVariablesViewQuotingPolicy().get());
            addPolicyActions(new QuotingPolicyAction(PyLocalize.debuggerVariablesViewQuotingSingleText().get(),
                                                     PyLocalize.debuggerVariablesViewQuotingSingleDescription().get(),
                                                     QuotingPolicy.SINGLE, this),
                             new QuotingPolicyAction(PyLocalize.debuggerVariablesViewQuotingDoubleText().get(),
                                                     PyLocalize.debuggerVariablesViewQuotingDoubleDescription().get(),
                                                     QuotingPolicy.DOUBLE, this),
                             new QuotingPolicyAction(PyLocalize.debuggerVariablesViewQuotingWithoutText().get(),
                                                     PyLocalize.debuggerVariablesViewQuotingWithoutDescription().get(),
                                                     QuotingPolicy.NONE, this));
        }

        @Override
        protected QuotingPolicy getDebuggerPolicy() {
            return PyDebuggerSettings.getInstance().getQuotingPolicy();
        }
    }

    public static final class SortingPolicyAction extends AbstractPolicyAction<SortingPolicy, SortingPolicyGroup> {
        private final BooleanSupplier myAvailable;

        public SortingPolicyAction(String text,
                                   String description,
                                   SortingPolicy policy,
                                   SortingPolicyGroup actionGroup,
                                   BooleanSupplier available) {
            super(text, description, policy, actionGroup);
            myAvailable = available;
        }

        @Override
        public void update(AnActionEvent e) {
            super.update(e);
            // Read on every update, because the session learns which debugger it talks to after the tab is built.
            e.getPresentation().setVisible(myAvailable.getAsBoolean());
        }

        @Override
        protected void changeDebuggerSettings() {
            PyDebuggerSettings.getInstance().setSortingPolicy(getPolicy());
        }
    }

    public static final class SortingPolicyGroup extends AbstractPolicyGroup<SortingPolicy, SortingPolicyAction> {
        private final @Nullable XDebugProcess myProcess;
        private final Supplier<SortingPolicy> myPolicyToMark;

        /**
         * @param policyToMark            the mode the session applies, which is the one to mark. It is not always the
         *                                stored mode, because a session can be unable to honor that one.
         * @param internalOrderAvailable  whether the internal mode is offered at all for this session.
         */
        public SortingPolicyGroup(@Nullable XDebugProcess debugProcess,
                                  Supplier<SortingPolicy> policyToMark,
                                  BooleanSupplier internalOrderAvailable) {
            super(PyLocalize.debuggerVariablesViewSortingPolicy().get());
            myProcess = debugProcess;
            myPolicyToMark = policyToMark;
            addPolicyActions(new SortingPolicyAction(PyLocalize.debuggerVariablesViewSortingInternalText().get(),
                                                     PyLocalize.debuggerVariablesViewSortingInternalDescription().get(),
                                                     SortingPolicy.DO_NOT_SORT, this, internalOrderAvailable),
                             new SortingPolicyAction(PyLocalize.debuggerVariablesViewSortingAlphabeticallyText().get(),
                                                     PyLocalize.debuggerVariablesViewSortingAlphabeticallyDescription().get(),
                                                     SortingPolicy.ALPHABETICALLY, this, () -> true),
                             new SortingPolicyAction(PyLocalize.debuggerVariablesViewSortingByTypeText().get(),
                                                     PyLocalize.debuggerVariablesViewSortingByTypeDescription().get(),
                                                     SortingPolicy.BY_TYPE, this, () -> true));
        }

        @Override
        protected SortingPolicy getDebuggerPolicy() {
            return myPolicyToMark.get();
        }

        @Override
        public void notifyPolicyUpdated() {
            super.notifyPolicyUpdated();
            // The tree holds the children in the previous order, so it must be built again.
            if (myProcess != null) {
                myProcess.getSession().rebuildViews();
            }
        }
    }

    private abstract static class AbstractPolicyAction<Policy extends AbstractPolicy, PolicyGroup extends AbstractPolicyGroup<Policy, ? extends ToggleAction>>
      extends ToggleAction {
        private final Policy myPolicy;
        private final PolicyGroup myActionGroup;
        private volatile boolean isEnabled;

        private AbstractPolicyAction(String text,
                                     String description,
                                     Policy policy,
                                     PolicyGroup actionGroup) {
            super(text, description, null);
            myPolicy = policy;
            myActionGroup = actionGroup;
            isEnabled = actionGroup.getDebuggerPolicy() == policy;
        }

        public PolicyGroup getActionGroup() {
            return myActionGroup;
        }

        public Policy getPolicy() {
            return myPolicy;
        }

        @Override
        public void update(AnActionEvent e) {
            super.update(e);
            myActionGroup.updatePolicyActions();
            Presentation presentation = e.getPresentation();
            presentation.setEnabled(true);
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        public void setEnabled(boolean enabled) {
            isEnabled = enabled;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return isEnabled;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean hide) {
            isEnabled = hide;
            if (hide) {
                changeDebuggerSettings();
                myActionGroup.notifyPolicyUpdated();
            }
            myActionGroup.updatePolicyActions();
        }

        protected abstract void changeDebuggerSettings();
    }

    public abstract static class AbstractPolicyGroup<Policy extends AbstractPolicy, PolicyAction extends AbstractPolicyAction<Policy, ? extends DefaultActionGroup>>
      extends DefaultActionGroup {
        private final List<PolicyAction> myPolicyActions = new ArrayList<>();
        private final List<PolicyListener> myPolicyListeners = new ArrayList<>();

        private AbstractPolicyGroup(String name) {
            super(name, true);
        }

        public void addPolicyActions(PolicyAction... actions) {
            myPolicyActions.addAll(List.of(actions));
            for (AnAction action : myPolicyActions) {
                add(action);
            }
        }

        public void notifyPolicyUpdated() {
            for (PolicyListener listener : myPolicyListeners) {
                listener.valuesPolicyUpdated();
            }
        }

        public void addValuesPolicyListener(PolicyListener listener) {
            myPolicyListeners.add(listener);
        }

        protected void updatePolicyActions() {
            AbstractPolicy currentPolicy = getDebuggerPolicy();
            for (PolicyAction action : myPolicyActions) {
                action.setEnabled(currentPolicy == action.getPolicy());
            }
        }

        protected abstract AbstractPolicy getDebuggerPolicy();
    }
}
