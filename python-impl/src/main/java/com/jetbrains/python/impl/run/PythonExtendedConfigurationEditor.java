/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.run;

import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposer;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
public class PythonExtendedConfigurationEditor<T extends AbstractPythonRunConfiguration<T>> extends SettingsEditor<T> {
    private final SettingsEditor<T> myMainSettingsEditor;

    private @Nullable PyRunConfigurationEditorExtension myCurrentEditor;
    private @Nullable SettingsEditor<AbstractPythonRunConfiguration> myCurrentSettingsEditor;
    private @Nullable Component myCurrentSettingsEditorComponent;

    private @Nullable VerticalLayout mySettingsPlaceholder;

    public PythonExtendedConfigurationEditor(SettingsEditor<T> editor) {
        myMainSettingsEditor = editor;

        Disposer.register(this, myMainSettingsEditor);
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(T s) {
        myMainSettingsEditor.resetFrom(s);
        updateCurrentEditor(s);
        SettingsEditor<AbstractPythonRunConfiguration> currentSettingsEditor = myCurrentSettingsEditor;
        if (currentSettingsEditor != null) {
            currentSettingsEditor.resetFrom(s);
        }
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(T s) throws ConfigurationException {
        myMainSettingsEditor.applyTo(s);
        boolean updated = updateCurrentEditor(s);
        SettingsEditor<AbstractPythonRunConfiguration> currentSettingsEditor = myCurrentSettingsEditor;
        if (currentSettingsEditor != null) {
            if (updated) {
                currentSettingsEditor.resetFrom(s);
            }
            else {
                currentSettingsEditor.applyTo(s);
            }
        }
    }

    @RequiredUIAccess
    private boolean updateCurrentEditor(T s) {
        PyRunConfigurationEditorExtension newEditor = PyRunConfigurationEditorExtension.Factory.getExtension(s);
        if (myCurrentEditor == newEditor) {
            return false;
        }

        VerticalLayout settingsPlaceholder = mySettingsPlaceholder;
        Component currentComponent = myCurrentSettingsEditorComponent;
        if (settingsPlaceholder != null && currentComponent != null) {
            settingsPlaceholder.remove(currentComponent);
        }
        myCurrentSettingsEditorComponent = null;

        SettingsEditor<AbstractPythonRunConfiguration> currentSettingsEditor = myCurrentSettingsEditor;
        if (currentSettingsEditor != null) {
            Disposer.dispose(currentSettingsEditor);
        }

        myCurrentEditor = newEditor;
        myCurrentSettingsEditor = null;

        if (newEditor != null) {
            SettingsEditor<AbstractPythonRunConfiguration> settingsEditor = newEditor.createEditor(s);
            myCurrentSettingsEditor = settingsEditor;
            Disposer.register(this, settingsEditor);

            Component component = settingsEditor.getUIComponent();
            myCurrentSettingsEditorComponent = component;
            if (settingsPlaceholder != null) {
                settingsPlaceholder.add(component);
            }
        }
        return true;
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        VerticalLayout settingsPlaceholder = VerticalLayout.create();
        settingsPlaceholder.add(myMainSettingsEditor.getUIComponent());

        Component currentComponent = myCurrentSettingsEditorComponent;
        if (currentComponent != null) {
            settingsPlaceholder.add(currentComponent);
        }

        mySettingsPlaceholder = settingsPlaceholder;
        return settingsPlaceholder;
    }

    public static <T extends AbstractPythonRunConfiguration<T>> PythonExtendedConfigurationEditor<T> create(SettingsEditor<T> editor) {
        return new PythonExtendedConfigurationEditor<>(editor);
    }
}
