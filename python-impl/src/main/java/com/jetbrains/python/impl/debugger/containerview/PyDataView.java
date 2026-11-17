// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.impl.console.PyConsoleProcessHandlers;
import com.jetbrains.python.impl.console.PydevConsoleCommunication;
import com.jetbrains.python.impl.console.PythonConsoleToolWindow;
import com.jetbrains.python.impl.console.PythonConsoleToolWindowFactory;
import com.jetbrains.python.impl.debugger.PyDebugProcess;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.disposer.Disposer;
import consulo.execution.ui.RunContentDescriptor;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.project.ProjectPropertiesComponent;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.content.event.ContentManagerEvent;
import consulo.ui.ex.content.event.ContentManagerListener;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowType;
import consulo.util.lang.Comparing;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyDataView implements DumbAware {
    private static final Logger LOG = Logger.getInstance(PyDataView.class);

    private static final String DATA_VIEWER_ID = "SciView";

    public static final String COLORED_BY_DEFAULT = "datagrid.heatmap.switchedByDefault";
    public static final String AUTO_RESIZE = "python.debugger.dataView.autoresize";

    private static final String HELP_ID = "reference.toolWindows.PyDataView";

    private final Project myProject;

    private final Map<ProcessHandler, Content> mySelectedInfos = new ConcurrentHashMap<>();

    private ContentManager myContentManager;
    private boolean myIsPythonConsoleListenerAttached = false;

    @Inject
    public PyDataView(Project project) {
        myProject = project;
    }

    public static PyDataView getInstance(Project project) {
        return project.getInstance(PyDataView.class);
    }

    public static boolean isAutoResizeEnabled(Project project) {
        return ProjectPropertiesComponent.getInstance(project).getBoolean(AUTO_RESIZE, true);
    }

    public static boolean isColoringEnabled(Project project) {
        return ProjectPropertiesComponent.getInstance(project).getBoolean(COLORED_BY_DEFAULT, true);
    }

    public static void setColoringEnabled(Project project, boolean value) {
        ProjectPropertiesComponent.getInstance(project).setValue(COLORED_BY_DEFAULT, value, true);
    }

    public static void setAutoResizeEnabled(Project project, boolean value) {
        ProjectPropertiesComponent.getInstance(project).setValue(AUTO_RESIZE, value, true);
    }

    public void init(ToolWindow toolWindow) {
        // TODO consulo: ToolWindow.setHelpId missing (JB: toolWindow.helpId = HELP_ID)
        toolWindow.setAvailable(true);

        myContentManager = toolWindow.getContentManager();

        addEmptyContent();
        attachPythonConsoleListenerIfAvailable(getPythonConsoleToolWindow(ToolWindowManager.getInstance(myProject)));

        myProject.getMessageBus().connect()
            .subscribe(
                ToolWindowManagerListener.class,
                new ToolWindowManagerListener() {
                    @Override
                    public void stateChanged(ToolWindowManager toolWindowManager) {
                        if (!myIsPythonConsoleListenerAttached) {
                            attachPythonConsoleListenerIfAvailable(getPythonConsoleToolWindow(toolWindowManager));
                        }
                        if (toolWindow.isAvailable() && toolWindow.getType() == ToolWindowType.FLOATING && !toolWindow.isVisible()) {
                            toolWindow.setShowStripeButton(false);
                            toolWindow.getContentManager().removeAllContents(true);
                        }
                    }
                });
    }

    private void addEmptyContent() {
        Content content = ContentFactory.getInstance().createContent(createEmptyContent(), null, false);
        content.setCloseable(false);
        myContentManager.addContent(content);
    }

    public void show(PyDebugValue value) {
        if (ToolWindowManager.getInstance(myProject).getToolWindow(DATA_VIEWER_ID) != null) {
            showInToolWindow(value);
        }
        else {
            Application.get().invokeLater(() -> new PyDataViewDialog(myProject, value).show());
        }
    }

    private void showInToolWindow(PyDebugValue value) {
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DATA_VIEWER_ID);
        if (window == null) {
            LOG.error("Tool window '" + DATA_VIEWER_ID + "' is not found");
            return;
        }
        // TODO consulo: ContentManager.getReady(requestor) missing (JB: window.contentManager.getReady(this).doWhenDone { ... })
        Content selectedInfo = addTab(value.getFrameAccessor());
        PyDataViewerPanel dataViewerPanel = (PyDataViewerPanel) selectedInfo.getComponent();
        dataViewerPanel.getComponent().apply(value, false);
        window.show(() -> {
            window.getComponent().requestFocusInWindow();
            dataViewerPanel.requestFocusInWindow();
        });
    }

    public void closeTabs(Predicate<PyFrameAccessor> ifClose) {
        List<Content> tabsToRemove = new ArrayList<>();

        for (Content content : myContentManager.getContents()) {
            if (ifClose.test(getPanel(content.getComponent()).getComponent().getDataViewerModel().getFrameAccessor())) {
                tabsToRemove.add(content);
            }
        }

        Application.get().invokeLater(() -> {
            for (Content content : tabsToRemove) {
                myContentManager.removeContent(content, true);
            }
        });
    }

    public void updateTabs(ProcessHandler handler) {
        saveSelectedInfo();
        for (Content content : myContentManager.getContents()) {
            PyDataViewerAbstractPanel panel = getPanel(content.getComponent()).getComponent();
            PyFrameAccessor accessor = panel.getDataViewerModel().getFrameAccessor();
            if (!(accessor instanceof PyDebugProcess debugProcess)) {
                continue;
            }

            boolean shouldBeShown = Comparing.equal(handler, debugProcess.getProcessHandler());
            if (!shouldBeShown) {
                myContentManager.removeContent(content, true);
            }
        }

        restoreSelectedInfo(handler);
    }

    private void restoreSelectedInfo(ProcessHandler handler) {
        Content savedSelection = mySelectedInfos.get(handler);
        if (savedSelection != null) {
            myContentManager.setSelectedContent(savedSelection);
            mySelectedInfos.remove(handler);
        }
    }

    private void saveSelectedInfo() {
        Content selectedInfo = myContentManager.getSelectedContent();
        if (!hasOnlyEmptyTab() && selectedInfo != null) {
            PyFrameAccessor accessor = getPanel(selectedInfo.getComponent()).getComponent().getDataViewerModel().getFrameAccessor();
            if (accessor instanceof PyDebugProcess debugProcess) {
                mySelectedInfos.put(debugProcess.getProcessHandler(), selectedInfo);
            }
        }
    }

    public void closeDisconnectedFromConsoleTabs() {
        closeTabs(frameAccessor -> frameAccessor instanceof PydevConsoleCommunication communication && !isConnected(communication));
    }

    public boolean isConnected(PydevConsoleCommunication accessor) {
        return !accessor.isCommunicationClosed();
    }

    private boolean attachPythonConsoleListenerIfAvailable(@Nullable ToolWindow pythonConsole) {
        if (myIsPythonConsoleListenerAttached) {
            return true;
        }

        ContentManager pythonConsoleContentManager = pythonConsole != null ? pythonConsole.getContentManagerIfCreated() : null;
        if (pythonConsoleContentManager == null) {
            return false;
        }
        pythonConsoleContentManager.addContentManagerListener(new PyDataContentManagerListener());
        myIsPythonConsoleListenerAttached = true;
        return true;
    }


    private static @Nullable ToolWindow getPythonConsoleToolWindow(ToolWindowManager toolWindowManager) {
        return toolWindowManager.getToolWindow(PythonConsoleToolWindowFactory.ID);
    }

    private void addTabConnectedToSelectedConsole() {
        RunContentDescriptor descriptor = PythonConsoleToolWindow.getInstance(myProject).getSelectedContentDescriptor();
        if (descriptor == null) {
            return;
        }
        ProcessHandler processHandler = descriptor.getProcessHandler();
        PydevConsoleCommunication communication =
            processHandler != null ? processHandler.getUserData(PyConsoleProcessHandlers.pydevConsoleCommunicationKey) : null;
        if (communication == null) {
            return;
        }
        addTab(communication);
    }

    private static JComponent createEmptyContent() {
        // JB builds this with the Kotlin UI DSL: panel { row { panel { row { text(...) } } }.resizableRow() }
        JPanel panel = new JPanel(new BorderLayout());
        JBLabel label = new JBLabel(PyLocalize.debuggerDataViewEmptyText().get());
        label.setBorder(JBUI.Borders.empty(8));
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    public Content addTab(PyFrameAccessor frameAccessor) {
        if (hasOnlyEmptyTab()) {
            myContentManager.removeAllContents(true);
        }

        PyDataViewerPanel panel = new PyDataViewerPanel(myProject, frameAccessor);

        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        content.setCloseable(true);
        content.setDisplayName(PyLocalize.debuggerDataViewEmptyTab().get());
        if (frameAccessor instanceof PydevConsoleCommunication) {
            content.setIcon(PythonImplIconGroup.pythonPythonconsole());
            content.setDescription(PyLocalize.debuggerDataViewConnectedToPythonConsole().get());
        }
        else if (frameAccessor instanceof PyDebugProcess debugProcess) {
            content.setIcon(PlatformIconGroup.toolwindowsToolwindowdebugger());
            content.setDescription(PyLocalize.debuggerDataViewConnectedToDebugSession(debugProcess.getSession().getSessionName()).get());
        }

        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DATA_VIEWER_ID);
        if (window != null) {
            window.setTabActions(new NewViewerAction(frameAccessor));
        }
        panel.addListener(content::setDisplayName);
        Disposer.register(content, panel);

        myContentManager.addContent(content);
        myContentManager.setSelectedContent(content);

        return content;
    }

    private boolean hasOnlyEmptyTab() {
        return myContentManager.getContentCount() == 1 && !(myContentManager.getContents()[0].getComponent() instanceof PyDataViewerPanel);
    }

    public List<Content> getVisibleTabs() {
        return Arrays.asList(myContentManager.getContents()); //myTabs.tabs.filter { tabInfo: TabInfo -> !tabInfo.isHidden }
    }

    public class NewViewerAction extends DumbAwareAction {
        private final PyFrameAccessor myFrameAccessor;

        public NewViewerAction(PyFrameAccessor frameAccessor) {
            super(
                PyLocalize.debuggerDataViewViewNewContainer(),
                PyLocalize.debuggerDataViewOpenNewContainerViewer(),
                PlatformIconGroup.generalAdd()
            );
            myFrameAccessor = frameAccessor;
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            addTab(myFrameAccessor);
        }
    }

    public void changeAutoResize(boolean autoResize) {
        for (Content content : myContentManager.getContents()) {
            ((PyDataViewerCommunityPanel) getPanel(content.getComponent()).getComponent()).resize(autoResize);
        }
    }

    public PyDataViewerPanel getPanel(JComponent component) {
        return (PyDataViewerPanel) component;
    }

    public class PyDataContentManagerListener implements ContentManagerListener {
        @Override
        public void contentRemoved(ContentManagerEvent event) {
            closeDisconnectedFromConsoleTabs();
        }

        @Override
        public void selectionChanged(ContentManagerEvent event) {
            if (!getVisibleTabs().isEmpty()) {
                return;
            }
            addTabConnectedToSelectedConsole();
        }
    }
}
