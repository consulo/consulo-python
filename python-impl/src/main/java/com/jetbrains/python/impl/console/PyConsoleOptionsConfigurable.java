// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JB builds this page with the Kotlin UI DSL. Consulo builds it with the unified UI and binds the values by hand.
 * <p>
 * Consulo-specific: the page is placed in the Execution group, and child pages are identified by
 * {@link PyConsoleOptionsProvider#getId()} because their names are localized.
 */
@ExtensionImpl
public class PyConsoleOptionsConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll, ProjectConfigurable {
    private static final String CONSOLE_SETTINGS_HELP_REFERENCE = "reference.project.settings.console";

    public enum CodeCompletionOption {
        RUNTIME, STATIC;

        @Override
        public String toString() {
            return switch (this) {
                case RUNTIME -> PyLocalize.formConsoleOptionsRuntimeCodeCompletion().get();
                case STATIC -> PyLocalize.formConsoleOptionsStaticCodeCompletion().get();
            };
        }
    }

    private final Project myProject;

    private @Nullable OwnContent myPanel;

    /**
     * The former "Python Console" child page. Its settings now live on this page.
     * <p>
     * Recreated for every {@link #createUIComponent} call: the panel owns a one-shot {@link Disposable}
     * for its SDK-table subscription, so a reused instance would subscribe to a disposed parent.
     */
    private @Nullable PyConsoleSpecificOptionsPanel myPythonConsolePanel;

    @Inject
    public PyConsoleOptionsConfigurable(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return "pyconsole";
    }

    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.EXECUTION_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.configurablePyconsoleoptionsconfigurableDisplayName();
    }

    @Override
    public String getHelpTopic() {
        return CONSOLE_SETTINGS_HELP_REFERENCE;
    }

    @Override
    public boolean hasOwnContent() {
        return true;
    }

    @Override
    protected Configurable[] buildConfigurables() {
        List<Configurable> result = new ArrayList<>();
        // The Python console page is merged into this one. Only the framework consoles stay as child pages.
        for (PyConsoleOptionsProvider provider : PyConsoleOptionsProvider.EP_NAME.getExtensionList()) {
            if (provider.isApplicableTo(myProject)) {
                result.add(createChildConfigurable(
                    provider.getId(),
                    provider.getName(),
                    new PyConsoleSpecificOptionsPanel(myProject),
                    provider.getSettings(myProject),
                    provider.getHelpTopic()
                ));
            }
        }
        return result.toArray(new Configurable[0]);
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(Disposable parentDisposable) {
        if (myPanel == null) {
            myPanel = createPanel();
        }
        return myPanel.myComponent;
    }

    // myPanel guards every call below. The platform may ask about state before createUIComponent, and
    // PyConsoleSpecificOptionsPanel keeps its settings in nullable fields until its panel is built.
    @RequiredUIAccess
    @Override
    public boolean isModified() {
        OwnContent panel = myPanel;
        return panel != null && (panel.isModified() || myPythonConsolePanel != null && myPythonConsolePanel.isModified());
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        OwnContent panel = myPanel;
        if (panel != null) {
            panel.apply();
            if (myPythonConsolePanel != null) {
                myPythonConsolePanel.apply();
            }
        }
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        OwnContent panel = myPanel;
        if (panel != null) {
            panel.reset();
            if (myPythonConsolePanel != null) {
                myPythonConsolePanel.reset();
            }
        }
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myPanel = null;
        if (myPythonConsolePanel != null) {
            myPythonConsolePanel.disposeUIResources();
        }
        myPythonConsolePanel = null;
        super.disposeUIResources();
    }

    @RequiredUIAccess
    private OwnContent createPanel() {
        PyConsoleOptions consoleOptions = PyConsoleOptions.getInstance(myProject);
        PyConsoleSpecificOptionsPanel pythonConsolePanel = new PyConsoleSpecificOptionsPanel(myProject);
        myPythonConsolePanel = pythonConsolePanel;
        Component pythonConsole = pythonConsolePanel.createPanel(consoleOptions.getPythonConsoleSettings());
        return new OwnContent(consoleOptions, pythonConsole);
    }

    /**
     * Consulo-specific: the own content of the page with its value bindings (JB: DialogPanel with bindSelected/bindItem).
     */
    private static final class OwnContent {
        private final PyConsoleOptions myConsoleOptions;
        private final CheckBox myIpythonEnabled;
        private final CheckBox myShowVariablesByDefault;
        private final CheckBox myUseExistingConsole;
        private final CheckBox myCommandQueueEnabled;
        private final ComboBox<CodeCompletionOption> myCodeCompletion;
        private final Component myComponent;

        @RequiredUIAccess
        private OwnContent(PyConsoleOptions consoleOptions, Component pythonConsole) {
            myConsoleOptions = consoleOptions;
            myIpythonEnabled = CheckBox.create(PyLocalize.formConsoleOptionsUseIpythonIfAvailable());
            myShowVariablesByDefault = CheckBox.create(PyLocalize.formConsoleOptionsShowConsoleVariablesByDefault());
            myUseExistingConsole = CheckBox.create(PyLocalize.formConsoleOptionsUseExistingConsoleForRunWithPythonConsole());
            myCommandQueueEnabled = CheckBox.create(PyLocalize.formConsoleOptionsUseCommandQueue());
            myCodeCompletion = ComboBox.create(CodeCompletionOption.values());

            VerticalLayout root = VerticalLayout.create();
            root.add(myIpythonEnabled);
            root.add(myShowVariablesByDefault);
            root.add(myUseExistingConsole);
            root.add(myCommandQueueEnabled);
            root.add(LabeledBuilder.simple(PyLocalize.formConsoleOptionsCodeCompletion(), myCodeCompletion));
            root.add(pythonConsole);
            myComponent = root;

            reset();
        }

        @RequiredUIAccess
        private boolean isModified() {
            return myIpythonEnabled.getValueOrError() != myConsoleOptions.isIpythonEnabled()
                || myShowVariablesByDefault.getValueOrError() != myConsoleOptions.isShowVariableByDefault()
                || myUseExistingConsole.getValueOrError() != myConsoleOptions.isUseExistingConsole()
                || myCommandQueueEnabled.getValueOrError() != myConsoleOptions.isCommandQueueEnabled()
                || myCodeCompletion.getValue() != myConsoleOptions.getCodeCompletionOption();
        }

        @RequiredUIAccess
        private void apply() {
            myConsoleOptions.setIpythonEnabled(myIpythonEnabled.getValueOrError());
            myConsoleOptions.setShowVariablesByDefault(myShowVariablesByDefault.getValueOrError());
            myConsoleOptions.setUseExistingConsole(myUseExistingConsole.getValueOrError());
            myConsoleOptions.setCommandQueueEnabled(myCommandQueueEnabled.getValueOrError());
            CodeCompletionOption codeCompletionOption = myCodeCompletion.getValue();
            if (codeCompletionOption != null) {
                myConsoleOptions.setCodeCompletionOption(codeCompletionOption);
            }
        }

        @RequiredUIAccess
        private void reset() {
            myIpythonEnabled.setValue(myConsoleOptions.isIpythonEnabled());
            myShowVariablesByDefault.setValue(myConsoleOptions.isShowVariableByDefault());
            myUseExistingConsole.setValue(myConsoleOptions.isUseExistingConsole());
            myCommandQueueEnabled.setValue(myConsoleOptions.isCommandQueueEnabled());
            myCodeCompletion.setValue(myConsoleOptions.getCodeCompletionOption());
        }
    }

    private static SearchableConfigurable createChildConfigurable(
        String id,
        LocalizeValue name,
        PyConsoleSpecificOptionsPanel panel,
        PyConsoleOptions.PyConsoleSettings settings,
        String helpReference
    ) {
        return new ChildConfigurable(id, name, panel, settings, helpReference);
    }

    private static final class ChildConfigurable implements SearchableConfigurable, Configurable.NoScroll {
        private final String myId;
        private final LocalizeValue myName;
        private final PyConsoleSpecificOptionsPanel myPanel;
        private final PyConsoleOptions.PyConsoleSettings mySettings;
        private final String myHelpReference;

        private ChildConfigurable(
            String id,
            LocalizeValue name,
            PyConsoleSpecificOptionsPanel panel,
            PyConsoleOptions.PyConsoleSettings settings,
            String helpReference
        ) {
            myId = id;
            myName = name;
            myPanel = panel;
            mySettings = settings;
            myHelpReference = helpReference;
        }

        @Override
        public String getId() {
            return "PyConsoleConfigurable." + myId;
        }

        @Override
        public LocalizeValue getDisplayName() {
            return myName;
        }

        @Override
        public String getHelpTopic() {
            return myHelpReference;
        }

        @RequiredUIAccess
        @Override
        public Component createUIComponent(Disposable parentDisposable) {
            return myPanel.createPanel(mySettings);
        }

        @RequiredUIAccess
        @Override
        public boolean isModified() {
            return myPanel.isModified();
        }

        @RequiredUIAccess
        @Override
        public void apply() throws ConfigurationException {
            myPanel.apply();
        }

        @RequiredUIAccess
        @Override
        public void reset() {
            myPanel.reset();
        }

        @RequiredUIAccess
        @Override
        public void disposeUIResources() {
            myPanel.disposeUIResources();
        }
    }
}
