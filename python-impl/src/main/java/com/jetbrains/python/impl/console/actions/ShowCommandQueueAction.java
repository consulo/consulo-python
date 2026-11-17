// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console.actions;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.PyConsoleUtil;
import com.jetbrains.python.impl.console.PythonConsoleView;
import consulo.application.dumb.DumbAware;
import consulo.execution.ExecutionUtil;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.image.Image;

import static com.jetbrains.python.impl.console.PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY;

/***
 * Action for showing the CommandQueue window
 */
public class ShowCommandQueueAction extends ToggleAction implements DumbAware {
    private static final Image emptyQueueIcon = PythonImplIconGroup.pythonCommandqueue();
    private static final Image notEmptyQueueIcon = ExecutionUtil.getIconWithLiveIndicator(emptyQueueIcon);

    private final PythonConsoleView consoleView;

    public ShowCommandQueueAction(PythonConsoleView consoleView) {
        super(
            PyLocalize.pythonConsoleCommandQueueShowActionText(),
            PyLocalize.pythonConsoleCommandQueueShowActionDescription(),
            emptyQueueIcon
        );
        this.consoleView = consoleView;
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        super.update(e);
        ConsoleCommunication communication = consoleView.getFile().getCopyableUserData(CONSOLE_COMMUNICATION_KEY);
        if (communication != null) {
            if (consoleView.isCommandQueueEnabled()) {
                e.getPresentation().setEnabled(true);

                if (PyConsoleUtil.isCommandQueueEmpty(consoleView.getProject(), communication)) {
                    e.getPresentation().setIcon(emptyQueueIcon);
                }
                else {
                    e.getPresentation().setIcon(notEmptyQueueIcon);
                }
            }
            else {
                e.getPresentation().setIcon(emptyQueueIcon);
                e.getPresentation().setEnabled(false);
            }
        }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return consoleView.isShowQueue();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        consoleView.setShowQueue(state);
        if (state) {
            consoleView.showQueue();
        }
        else {
            consoleView.restoreQueueWindow(false);
        }
    }

    public static boolean isCommandQueueIcon(Image icon) {
        return icon == emptyQueueIcon || icon == notEmptyQueueIcon;
    }
}
