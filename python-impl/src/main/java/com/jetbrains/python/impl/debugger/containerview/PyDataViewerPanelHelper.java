// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.popup.Balloon;
import consulo.ui.ex.popup.BalloonBuilder;
import consulo.ui.ex.popup.JBPopupFactory;

import javax.swing.JPanel;
import java.awt.Point;

final class PyDataViewerPanelHelper {
    private PyDataViewerPanelHelper() {
    }

    static void showCommunityDataViewerRestrictionsBalloon(PyDataViewerAbstractPanel tablePanel, PyDataViewerPanel tableParentPanel) {
        JPanel topToolbar = tablePanel.getTopToolbar();
        createBalloon(tablePanel)
            .show(
                new RelativePoint(
                    tableParentPanel,
                    new Point(tableParentPanel.getWidth() - (topToolbar != null ? topToolbar.getPreferredSize().width : 10) / 2,
                        topToolbar != null ? topToolbar.getPreferredSize().height : 0)
                ),
                Balloon.Position.below
            );
    }

    private static Balloon createBalloon(PyDataViewerAbstractPanel component) {
        BalloonBuilder builder = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(PyLocalize.debuggerDataviewerDataframesUnsupported().get(), NotificationType.INFO, null);
        builder.setDisposable(component);
        builder.setHideOnAction(true);
        builder.setHideOnClickOutside(true);
        return builder.createBalloon();
    }

    /**
     * GotIt Tooltip on the button with advertising of new and old table modes for viewing arrays in debug.
     */
    static void createGotItTooltip(PyDataViewerAbstractPanel tablePanel, PyDataViewerPanel tableParentPanel) {
        // TODO consulo: GotItTooltip missing. JB shows GotItTooltip("py.data.view.new.table",
        //  PyLocalize.debuggerDataviewerSwitchBetweenTablesGotitText()) with the link
        //  PyLocalize.debuggerDataviewerSwitchBetweenTablesGotitLink() -> tableParentPanel.switchBetweenCommunityAndFactoriesTables(),
        //  show count 1 and the balloon-information icon, below the right edge of tablePanel.getTopToolbar()
    }
}
