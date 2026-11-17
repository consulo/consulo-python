// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.PyDebugValue;
import consulo.project.Project;
import consulo.project.ProjectPropertiesComponent;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBUI;
import org.jspecify.annotations.Nullable;

import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

import static com.jetbrains.python.impl.debugger.containerview.PyDataView.isAutoResizeEnabled;
import static com.jetbrains.python.impl.debugger.containerview.PyDataView.isColoringEnabled;
import static com.jetbrains.python.impl.debugger.containerview.PyDataView.setAutoResizeEnabled;
import static com.jetbrains.python.impl.debugger.containerview.PyDataView.setColoringEnabled;

public class PyDataViewDialog extends DialogWrapper {
    private static final int TABLE_DEFAULT_WIDTH = 700;
    private static final int TABLE_DEFAULT_HEIGHT = 500;

    private static final String JUPYTER_SUGGESTION_ENABLED_PROPERTY_KEY = "python.debugger.dataView.jupyter.suggestion.enabled";

    private final Project myProject;
    private final JPanel myMainPanel;
    private final PyDataViewerCommunityPanel myDataViewerPanel;
    private @Nullable JPanel myJupyterSuggestionPanel;

    public PyDataViewDialog(Project project, PyDebugValue value) {
        super(project, false);
        myProject = project;

        setModal(false);
        setCancelButtonText(PyLocalize.debuggerDataViewClose());
        setCrossClosesWindow(true);

        myDataViewerPanel = new PyDataViewerCommunityPanel(new PyDataViewerModel(project, value.getFrameAccessor()));
        myDataViewerPanel.apply(value, false, null);
        myDataViewerPanel.setPreferredSize(JBUI.size(TABLE_DEFAULT_WIDTH, TABLE_DEFAULT_HEIGHT));

        myMainPanel = new JPanel(new BorderLayout());

        // If PythonPro installed and enabled.
        JPanel jupyterSuggestionPanel = createJupyterSuggestionPanel();
        myJupyterSuggestionPanel = jupyterSuggestionPanel;
        if (jupyterSuggestionPanel != null) {
            myMainPanel.add(jupyterSuggestionPanel, BorderLayout.NORTH);
        }

        myMainPanel.add(myDataViewerPanel, BorderLayout.CENTER);

        myDataViewerPanel.addNameChangedListener(this::setTitle);

        myMainPanel.add(createBottomElements(), BorderLayout.SOUTH);

        setTitle(value.getFullName());
        init();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#com.jetbrains.python.debugger.containerview.PyDataViewDialog";
    }

    private JPanel createBottomElements() {
        JBCheckBox colored = new JBCheckBox(PyLocalize.debuggerDataViewColoredCells().get());
        colored.setSelected(isColoringEnabled(myProject));
        colored.addActionListener(e -> {
            setColoringEnabled(myProject, colored.isSelected());
            myDataViewerPanel.getDataViewerModel().setColored(colored.isSelected());
            myDataViewerPanel.setColoredValueFromUI(colored.isSelected());
        });

        JBCheckBox resize = new JBCheckBox(PyLocalize.debuggerDataViewResizeAutomatically().get());
        resize.setSelected(isAutoResizeEnabled(myProject));
        resize.addActionListener(e -> {
            setAutoResizeEnabled(myProject, resize.isSelected());
            myDataViewerPanel.resize(resize.isSelected());
            myDataViewerPanel.updateUI();
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(colored);
        panel.add(resize);
        return panel;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
        return myMainPanel;
    }

    private @Nullable JPanel createJupyterSuggestionPanel() {
        if (!isRichTableAndJupyterCanBeEnabled()) {
            return null;
        }

        // TODO consulo: PluginManagerCore / PluginId / installAndEnable / PluginManagerConfigurable.shutdownOrRestartApp /
        //  EditorNotificationPanel missing. JB builds an EditorNotificationPanel with the text
        //  PyLocalize.debuggerDataViewReachViewSuggestion() and "Install"/"Enable" plugin and "Ignore" action labels for the
        //  PythonPro and Jupyter plugins; "Ignore" calls disableJupyterSuggestions(project) and hideJupyterSuggestionPanel().
        return null;
    }

    /**
     * Determines if enhanced table features can be enabled.
     * <p>
     * Checks three conditions:
     * 1. Running Ultimate Edition
     * 2. Jupyter suggestion is enabled
     * 3. Ultimate plugin is active
     */
    private boolean isRichTableAndJupyterCanBeEnabled() {
        // TODO consulo: PlatformUtils.isCommunityEdition() and PluginManagerCore.ULTIMATE_PLUGIN_ID missing; Consulo has no Ultimate
        //  edition, so isUltimateEdition and isUltimatePluginEnabled are always false
        boolean isUltimateEdition = false;
        boolean isJupyterEnabled = isJupyterSuggestionEnabled(myProject);
        boolean isUltimatePluginEnabled = false;

        return isUltimateEdition
            && isJupyterEnabled
            && isUltimatePluginEnabled;
    }

    private void hideJupyterSuggestionPanel() {
        JPanel panel = myJupyterSuggestionPanel;
        if (panel != null) {
            myMainPanel.remove(panel);
            myMainPanel.revalidate();
            myJupyterSuggestionPanel = null;
        }
    }

    private static boolean isJupyterSuggestionEnabled(Project project) {
        return ProjectPropertiesComponent.getInstance(project).getBoolean(JUPYTER_SUGGESTION_ENABLED_PROPERTY_KEY, true);
    }

    private static void disableJupyterSuggestions(Project project) {
        ProjectPropertiesComponent.getInstance(project).setValue(JUPYTER_SUGGESTION_ENABLED_PROPERTY_KEY, false, true);
    }
}
