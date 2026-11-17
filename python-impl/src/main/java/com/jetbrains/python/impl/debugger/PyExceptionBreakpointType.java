// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.google.common.collect.Maps;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointType;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.PyClassTreeChooserDialog;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.PyUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.function.Predicate;


@ExtensionImpl
public final class PyExceptionBreakpointType
  extends XBreakpointType<XBreakpoint<PyExceptionBreakpointProperties>, PyExceptionBreakpointProperties> {

    private static final String BASE_EXCEPTION = "BaseException";

    public PyExceptionBreakpointType() {
        super("python-exception", PyLocalize.debuggerExceptionBreakpointType().get());
    }

    @Override
    public Image getEnabledIcon() {
        return ExecutionDebugIconGroup.breakpointBreakpointexception();
    }

    @Override
    public Image getDisabledIcon() {
        return ExecutionDebugIconGroup.breakpointBreakpointexceptiondisabled();
    }

    @Override
    public PyExceptionBreakpointProperties createProperties() {
        return new PyExceptionBreakpointProperties(BASE_EXCEPTION);
    }

    @Override
    public boolean isAddBreakpointButtonVisible() {
        return true;
    }

    @Override
    public XBreakpoint<PyExceptionBreakpointProperties> addBreakpoint(Project project, JComponent parentComponent) {
        PyClassTreeChooserDialog dialog = new PyClassTreeChooserDialog(PyLocalize.debuggerExceptionBreakpointSelectExceptionClass().get(),
                                                                             project,
                                                                             GlobalSearchScope.allScope(project),
                                                                             new PyExceptionCachingFilter(), null);

        dialog.showDialog();

        // on ok
        PyClass pyClass = dialog.getSelected();
        if (pyClass != null) {
            String qualifiedName = pyClass.getQualifiedName();
            assert qualifiedName != null : "Qualified name of the class shouldn't be null";
            return XDebuggerManager.getInstance(project).getBreakpointManager()
              .addBreakpoint(this, new PyExceptionBreakpointProperties(qualifiedName));
        }
        return null;
    }

    private static class PyExceptionCachingFilter implements Predicate<PyClass> {
        private final HashMap<Integer, Pair<WeakReference<PyClass>, Boolean>> processedElements = Maps.newHashMap();

        @Override
        public boolean test(PyClass pyClass) {
            VirtualFile virtualFile = pyClass.getContainingFile().getVirtualFile();
            if (virtualFile == null) {
                return false;
            }

            int key = pyClass.hashCode();
            Pair<WeakReference<PyClass>, Boolean> pair = processedElements.get(key);
            boolean isException;
            if (pair == null || pair.first.get() != pyClass) {
                isException = ReadAction.compute(() -> PyUtil.isExceptionClass(pyClass));
                processedElements.put(key, Pair.create(new WeakReference<>(pyClass), isException));
            }
            else {
                isException = pair.second;
            }
            return isException;
        }
    }

    @Override
    public String getBreakpointsDialogHelpTopic() {
        return "reference.dialogs.breakpoints";
    }

    @Override
    public String getDisplayText(XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
        PyExceptionBreakpointProperties properties = breakpoint.getProperties();
        if (properties != null) {
            String exception = properties.getException();
            if (BASE_EXCEPTION.equals(exception)) {
                return PyLocalize.debuggerExceptionBreakpointAnyException().get();
            }
            return exception;
        }
        return "";
    }

    @Override
    public @Nullable XDebuggerEditorsProvider getEditorsProvider(XBreakpoint<PyExceptionBreakpointProperties> breakpoint,
                                                                 Project project) {
        return new PyDebuggerEditorsProvider();
    }

    @Override
    public XBreakpoint<PyExceptionBreakpointProperties> createDefaultBreakpoint(XBreakpointCreator<PyExceptionBreakpointProperties> creator) {
        XBreakpoint<PyExceptionBreakpointProperties> breakpoint = creator.createBreakpoint(createDefaultBreakpointProperties());
        breakpoint.setEnabled(true);
        return breakpoint;
    }

    private static PyExceptionBreakpointProperties createDefaultBreakpointProperties() {
        PyExceptionBreakpointProperties p = new PyExceptionBreakpointProperties(BASE_EXCEPTION);
        p.setNotifyOnTerminate(true);
        p.setNotifyOnlyOnFirst(false);
        return p;
    }

    @Override
    public XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> createCustomPropertiesPanel(Project project) {
        return new PyExceptionBreakpointPropertiesPanel();
    }


    private static class PyExceptionBreakpointPropertiesPanel
      extends XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> {
        private JCheckBox myNotifyOnTerminateCheckBox;
        private JCheckBox myNotifyOnRaiseCheckBox;
        private JCheckBox myIgnoreLibrariesCheckBox;
        private JBLabel myWarningIcon;

        @Override
        public JComponent getComponent() {
            myNotifyOnTerminateCheckBox = new JCheckBox(PyLocalize.debuggerExceptionBreakpointOnTermination().get());
            myNotifyOnRaiseCheckBox = new JCheckBox(PyLocalize.debuggerExceptionBreakpointOnRaise().get());
            myIgnoreLibrariesCheckBox = new JCheckBox(PyLocalize.debuggerExceptionBreakpointIgnoreLibraryFiles().get());

            Box notificationsBox = Box.createVerticalBox();
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(myNotifyOnTerminateCheckBox, BorderLayout.NORTH);
            notificationsBox.add(panel);
            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            panel.setBorder(JBUI.Borders.empty());
            panel.add(myNotifyOnRaiseCheckBox, Integer.valueOf(FlowLayout.LEFT));
            myWarningIcon = new JBLabel(PlatformIconGroup.generalBalloonwarning());
            // TODO consulo: IdeTooltipManager.setCustomTooltip and TooltipWithClickableLinks missing
            myWarningIcon.setToolTipText(PyLocalize.debuggerWarningMessage().get());
            myWarningIcon.setBorder(JBUI.Borders.emptyLeft(5));
            panel.add(myWarningIcon);
            notificationsBox.add(panel);
            panel = new JPanel(new BorderLayout());
            panel.add(myIgnoreLibrariesCheckBox, BorderLayout.NORTH);
            notificationsBox.add(panel);

            panel = new JPanel(new BorderLayout());
            JPanel innerPanel = new JPanel(new BorderLayout());
            innerPanel.add(notificationsBox, BorderLayout.CENTER);
            innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
            innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
            panel.add(innerPanel, BorderLayout.NORTH);
            panel.setBorder(IdeBorderFactory.createTitledBorder(PyLocalize.debuggerExceptionBreakpointActivationPolicy().get()));

            return panel;
        }

        @Override
        public void saveTo(XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
            breakpoint.getProperties().setNotifyOnTerminate(myNotifyOnTerminateCheckBox.isSelected());
            breakpoint.getProperties().setNotifyOnlyOnFirst(myNotifyOnRaiseCheckBox.isSelected());
            breakpoint.getProperties().setIgnoreLibraries(myIgnoreLibrariesCheckBox.isSelected());
        }

        @Override
        public void loadFrom(XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
            myIgnoreLibrariesCheckBox.setSelected(breakpoint.getProperties().isIgnoreLibraries());
            myNotifyOnTerminateCheckBox.setSelected(breakpoint.getProperties().isNotifyOnTerminate());
            myNotifyOnRaiseCheckBox.setSelected(breakpoint.getProperties().isNotifyOnlyOnFirst());
        }
    }
}


