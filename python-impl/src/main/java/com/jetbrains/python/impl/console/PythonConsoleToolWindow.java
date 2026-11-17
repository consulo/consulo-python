// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.dumb.DumbAware;
import consulo.disposer.Disposer;
import consulo.execution.ui.RunContentDescriptor;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.SimpleToolWindowPanel;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.util.dataholder.Key;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class PythonConsoleToolWindow {
    public static final Key<RunContentDescriptor> CONTENT_DESCRIPTOR = Key.create("CONTENT_DESCRIPTOR");

    public static final Function<Content, RunContentDescriptor> CONTENT_TO_DESCRIPTOR_FUNCTION = input -> {
        return input == null ? null : input.getUserData(CONTENT_DESCRIPTOR);
    };

    private final Project myProject;

    private boolean myInitialized = false;

    @Inject
    public PythonConsoleToolWindow(Project project) {
        myProject = project;
    }

    public static PythonConsoleToolWindow getInstance(Project project) {
        return project.getInstance(PythonConsoleToolWindow.class);
    }

    public List<RunContentDescriptor> getConsoleContentDescriptors() {
        return Arrays.stream(getToolWindow(myProject).getContentManager().getContents())
            .map(CONTENT_TO_DESCRIPTOR_FUNCTION)
            .filter(Objects::nonNull)
            .toList();
    }

    public void init(ToolWindow toolWindow, RunContentDescriptor contentDescriptor) {
        setContent(toolWindow, contentDescriptor);

        if (!myInitialized) {
            doInit(toolWindow);
        }

        toolWindow.setTabActions(new NewConsoleAction());
    }

    public boolean isInitialized() {
        return myInitialized;
    }

    private void doInit(ToolWindow toolWindow) {
        myInitialized = true;

        myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.class, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(ToolWindowManager toolWindowManager) {
                // TODO consulo: ToolWindowManagerEventType (ActivateToolWindow) missing, the visible state is checked instead
                if (toolWindow.isVisible() && toolWindow.getContentManager().getContentCount() == 0) {
                    PythonConsoleStarter.launchPythonConsoleRunnerIfInterpreterExists(myProject, null, runner -> runner.run(true));
                }
            }
        });
    }

    private static void setContent(ToolWindow toolWindow, RunContentDescriptor contentDescriptor) {
        // TODO consulo: ToolWindowContentUi.HIDE_ID_LABEL is internal in Consulo

        Content content = toolWindow.getContentManager().findContent(contentDescriptor.getDisplayName());
        if (content == null) {
            content = createContent(contentDescriptor);
            toolWindow.getContentManager().addContent(content);
        }
        else {
            SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
            resetContent(contentDescriptor, panel, content);
        }

        toolWindow.getContentManager().setSelectedContent(content);
    }

    public ToolWindow getToolWindow() {
        return getToolWindow(myProject);
    }

    public static ToolWindow getToolWindow(Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow consoleToolWindow = toolWindowManager.getToolWindow(PythonConsoleToolWindowFactory.ID);
        if (consoleToolWindow == null) {
            consoleToolWindow = toolWindowManager.registerToolWindow(PythonConsoleToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM);
            consoleToolWindow.setIcon(PythonImplIconGroup.pythonPythonconsoletoolwindow());
        }
        return consoleToolWindow;
    }

    public void setContent(RunContentDescriptor contentDescriptor) {
        setContent(getToolWindow(myProject), contentDescriptor);
    }

    private static Content createContent(RunContentDescriptor contentDescriptor) {
        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

        Content content = ContentFactory.getInstance().createContent(panel, contentDescriptor.getDisplayName(), false);
        content.setCloseable(true);

        resetContent(contentDescriptor, panel, content);

        return content;
    }

    private static void resetContent(RunContentDescriptor contentDescriptor, SimpleToolWindowPanel panel, Content content) {
        RunContentDescriptor oldDescriptor =
            content.getDisposer() instanceof RunContentDescriptor ? (RunContentDescriptor) content.getDisposer() : null;
        if (oldDescriptor != null) {
            Disposer.dispose(oldDescriptor);
        }

        panel.setContent(contentDescriptor.getComponent());

        content.setComponent(panel);
        content.setDisposer(contentDescriptor);
        content.setPreferredFocusableComponent(contentDescriptor.getComponent());
        content.setPreferredFocusedComponent(contentDescriptor.getPreferredFocusComputable());

        content.putUserData(CONTENT_DESCRIPTOR, contentDescriptor);
    }

    public void activate(Runnable runnable) {
        getToolWindow(myProject).activate(runnable);
    }

    public @Nullable RunContentDescriptor getSelectedContentDescriptor() {
        return CONTENT_TO_DESCRIPTOR_FUNCTION.apply(getToolWindow(myProject).getContentManager().getSelectedContent());
    }

    private static class NewConsoleAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
        NewConsoleAction() {
            super(PyLocalize.consoleNewConsole(), PyLocalize.consoleNewConsoleDescription(), PlatformIconGroup.generalAdd());
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(false);
            Project project = e.getData(Project.KEY);
            if (project == null) {
                return;
            }
            boolean hasInterpreter = PythonConsoleStarter.hasPythonConsoleInterpreter(project, e.getData(Module.KEY));
            PythonConsoleStarter.setConsoleInterpreterState(
                e.getPresentation(), hasInterpreter,
                PyLocalize.pythonConsoleNoInterpreterProject(), getTemplatePresentation().getDescription());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            Project project = e.getData(Project.KEY);
            if (project != null) {
                PythonConsoleStarter.launchPythonConsoleRunner(project, e.getData(Module.KEY), runner -> runner.run(true));
            }
        }
    }
}
