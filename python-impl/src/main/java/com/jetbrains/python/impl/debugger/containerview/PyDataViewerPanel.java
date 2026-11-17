// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import consulo.application.Application;
import consulo.application.ApplicationPropertiesComponent;
import consulo.application.util.registry.Registry;
import consulo.dataContext.DataManager;
import consulo.dataContext.UiDataProvider;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.util.dataholder.Key;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class PyDataViewerPanel extends JPanel implements Disposable {
    public static final Key<PyDataViewerPanel> PY_DATA_VIEWER_PANEL_KEY = Key.create("PY_DATA_VIEWER_PANEL_KEY");

    private static final String DATA_VIEWER_SHOW_COMMUNITY_PANEL = "py.data.viewer.powerful.table.shown.on.each.data.view.opening";

    private final Project myProject;
    private final PyFrameAccessor myFrameAccessor;
    private boolean myIsPanelFromFactory;

    private PyDataViewerAbstractPanel myComponent;

    public PyDataViewerPanel(Project project, PyFrameAccessor frameAccessor) {
        this(project, frameAccessor, false);
    }

    public PyDataViewerPanel(Project project, PyFrameAccessor frameAccessor, boolean isPanelFromFactory) {
        super(new BorderLayout());
        myProject = project;
        myFrameAccessor = frameAccessor;
        myIsPanelFromFactory = isPanelFromFactory;

        PyDataViewerModel dataViewerModel = new PyDataViewerModel(project, frameAccessor);

        PyDataViewerAbstractPanel dataViewerPanel;
        if (isPanelFromFactory()) {
            myIsPanelFromFactory = true;
            dataViewerPanel = createPanelFromFactory(dataViewerModel);
        }
        else {
            dataViewerPanel = new PyDataViewerCommunityPanel(dataViewerModel);
        }

        myComponent = dataViewerPanel;
        add(myComponent);

        setupDataProvider();

        if (isPanelsFromFactoryAvailable() && !myIsPanelFromFactory) {
            PyDataViewerPanelHelper.createGotItTooltip(myComponent, this);
        }
    }

    public Project getProject() {
        return myProject;
    }

    public PyFrameAccessor getFrameAccessor() {
        return myFrameAccessor;
    }

    public PyDataViewerAbstractPanel getComponent() {
        return myComponent;
    }

    public void setComponent(PyDataViewerAbstractPanel component) {
        myComponent = component;
    }

    @Override
    public void dispose() {
    }

    public PyDataViewerModel getDataViewerModel() {
        return myComponent.getDataViewerModel();
    }

    public void addListener(PyDataViewerAbstractPanel.OnNameChangedListener onNameChangedListener) {
        myComponent.addNameChangedListener(onNameChangedListener);
    }

    public void switchBetweenCommunityAndFactoriesTables() {
        myIsPanelFromFactory = !myIsPanelFromFactory;
        savePreferencesIfNeeded(myIsPanelFromFactory);

        PyDataViewerModel dataViewerModel = getDataViewerModel();
        PyDebugValue debugValue = dataViewerModel.getDebugValue();
        if (debugValue != null && !isSupportedByCommunityDataViewer(debugValue)) {
            myIsPanelFromFactory = true;
            PyDataViewerPanelHelper.showCommunityDataViewerRestrictionsBalloon(myComponent, this);
            return;
        }

        if (myIsPanelFromFactory && isPanelsFromFactoryAvailable()) {
            myComponent = createPanelFromFactory(dataViewerModel);
        }
        else {
            myComponent = new PyDataViewerCommunityPanel(dataViewerModel);
        }
        myComponent.recreateTable();

        removeAll();
        add(myComponent);

        if (debugValue != null) {
            myComponent.apply(debugValue, false);
        }
        else {
            myComponent.apply(dataViewerModel.getSlicing(), false);
        }
    }

    private static PyDataViewerAbstractPanel createPanelFromFactory(PyDataViewerModel dataViewerModel) {
        return Application.get().getExtensionList(PyDataViewPanelFactory.class).get(0).createDataViewerPanel(dataViewerModel);
    }

    /**
     * Old tables cannot work properly with polars dataframes.
     */
    private static boolean isSupportedByCommunityDataViewer(PyDebugValue debugValue) {
        return !"polars.dataframe.frame".equals(debugValue.getTypeQualifier());
    }

    private void setupDataProvider() {
        UiDataProvider toolbarDataProvider = sink -> sink.set(PY_DATA_VIEWER_PANEL_KEY, this);

        addDataProvider(this, toolbarDataProvider);
    }

    public static void addDataProvider(JComponent component, UiDataProvider provider) {
        // JB: DataManager.getDataProvider/removeDataProvider/registerDataProvider(CompositeDataProvider.compose(current, provider)).
        // Consulo keeps the registered provider in the UiDataProvider.KEY client property.
        if (component.getClientProperty(UiDataProvider.KEY) instanceof UiDataProvider currentProvider) {
            DataManager.registerUiDataProvider(component, sink -> {
                currentProvider.uiDataSnapshot(sink);
                provider.uiDataSnapshot(sink);
            });
        }
        else {
            DataManager.registerUiDataProvider(component, provider);
        }
    }

    private static boolean isPanelsFromFactoryAvailable() {
        return !Application.get().getExtensionList(PyDataViewPanelFactory.class).isEmpty();
    }

    private static boolean isPanelFromFactory() {
        if (!isPanelsFromFactoryAvailable()) {
            return false;
        }
        if (Registry.is("python.data.view.allow.save.preferences.community.vs.powerful.data.view", false)) {
            return isUsingCommunityPanel();
        }
        return true;
    }

    private static void savePreferencesIfNeeded(boolean newValue) {
        if (Registry.is("python.data.view.allow.save.preferences.community.vs.powerful.data.view", false)) {
            setUsingCommunityPanel(newValue);
        }
    }

    public static boolean isUsingCommunityPanel() {
        return ApplicationPropertiesComponent.getInstance().getBoolean(DATA_VIEWER_SHOW_COMMUNITY_PANEL, true);
    }

    public static void setUsingCommunityPanel(boolean value) {
        ApplicationPropertiesComponent.getInstance().setValue(DATA_VIEWER_SHOW_COMMUNITY_PANEL, value, true);
    }
}
