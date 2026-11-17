// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.Hyperlink;
import consulo.ui.ImageBox;
import consulo.ui.IntBox;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * JB builds this page with the Kotlin UI DSL. Consulo builds it with the unified UI.
 * <p>
 * Consulo-specific: there is no DAP backend switching (the debugger is always pydevd), so the pydevd/debugpy
 * backend radio buttons and descriptions are not ported, and the rows JB shows for pydevd are always shown.
 * The "Gevent compatible" row, which JB shows for debugpy only, is kept because Consulo's PyDebugRunner
 * still passes GEVENT_SUPPORT to pydevd.
 */
@ExtensionImpl
public class PyDebuggerConfigurable extends SimpleConfigurableByProperties implements Configurable.NoScroll, ProjectConfigurable {
    private enum PyQtBackend {
        AUTO(() -> PyLocalize.pythonDebuggerQtBackendAuto().get()),
        PYQT4(() -> "PyQt4"),
        PYQT5(() -> "PyQt5"),
        PYQT6(() -> "PyQt6"),
        PYSIDE(() -> "PySide"),
        PYSIDE2(() -> "PySide2"),
        PYSIDE6(() -> "PySide6");

        private final Supplier<String> myDisplayNameSupplier;

        PyQtBackend(Supplier<String> displayNameSupplier) {
            myDisplayNameSupplier = displayNameSupplier;
        }

        @Override
        public String toString() {
            return myDisplayNameSupplier.get();
        }
    }

    private final Project myProject;

    @Inject
    public PyDebuggerConfigurable(Project project) {
        myProject = project;
    }

    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.EXECUTION_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.configurablePydebuggerconfigurableDisplayName();
    }

    @Override
    public String getHelpTopic() {
        return "reference.idesettings.debugger.python";
    }

    @Override
    public String getId() {
        return getHelpTopic();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
        VerticalLayout root = VerticalLayout.create();

        CheckBox attachToSubprocess = CheckBox.create(PyLocalize.formDebuggerAttachToSubprocessAutomaticallyWhileDebugging());
        propertyBuilder.add(attachToSubprocess, settings::isAttachToSubprocess, settings::setAttachToSubprocess);
        root.add(attachToSubprocess);

        CheckBox saveSignatures = CheckBox.create(PyLocalize.formDebuggerCollectRunTimeTypesInformationForCodeInsight());
        propertyBuilder.add(saveSignatures, settings::isSaveCallSignatures, settings::setSaveCallSignatures);
        ImageBox warningIcon = ImageBox.create(PlatformIconGroup.generalBalloonwarning());
        warningIcon.setToolTipText(PyLocalize.debuggerWarningMessage());
        Hyperlink clearCaches = Hyperlink.create(PyLocalize.formDebuggerClearCaches(), event -> {
            boolean cleared = PySignatureCacheManager.getInstance(myProject).clearCache();
            LocalizeValue msg = cleared ? PyLocalize.pythonDebuggerCollectionSignaturesDeleted()
                : PyLocalize.pythonDebuggerNothingToDelete();
            Messages.showInfoMessage(myProject, msg.get(), PyLocalize.debuggerDeleteSignatureCache().get());
        });
        root.add(HorizontalLayout.create().add(saveSignatures).add(warningIcon).add(clearCaches));

        // Consulo-specific: kept, see the class javadoc
        CheckBox supportGevent = CheckBox.create(PyLocalize.formDebuggerGeventCompatible());
        propertyBuilder.add(supportGevent, settings::isSupportGeventDebugging, settings::setSupportGeventDebugging);
        root.add(supportGevent);

        CheckBox dropIntoDebugger = CheckBox.create(PyLocalize.formDebuggerDropIntoDebuggerOnFailedTests());
        propertyBuilder.add(dropIntoDebugger, settings::isDropIntoDebuggerOnFailedTest, settings::setDropIntoDebuggerOnFailedTest);
        root.add(dropIntoDebugger);

        CheckBox supportQt = CheckBox.create(PyLocalize.formDebuggerPyqtCompatible());
        propertyBuilder.add(supportQt, settings::isSupportQtDebugging, settings::setSupportQtDebugging);
        ComboBox<PyQtBackend> qtBackend = ComboBox.create(PyQtBackend.values());
        propertyBuilder.add(
            qtBackend,
            () -> PyQtBackend.valueOf(settings.getPyQtBackend().toUpperCase(Locale.ROOT)),
            it -> {
                if (it != null) {
                    settings.setPyQtBackend(it.name().toLowerCase(Locale.ROOT));
                }
            }
        );
        // JB: .enabledIf(qtCell.component.selected)
        supportQt.addValueListener(event -> qtBackend.setEnabled(event.getValue()));
        root.add(HorizontalLayout.create().add(supportQt).add(qtBackend));

        TextBox attachProcessFilter = TextBox.create();
        propertyBuilder.add(attachProcessFilter, settings::getAttachProcessFilter, settings::setAttachProcessFilter);
        root.add(LabeledBuilder.simple(PyLocalize.debuggerAttachToProcessFilterNames(), attachProcessFilter));

        // TODO consulo: IntBox has no spinner step, JB uses EVALUATION_TIMEOUT_STEP_MS = 500
        IntBox evaluationTimeout = IntBox.create(settings.getEvaluationResponseTimeout()).withRange(0, Integer.MAX_VALUE);
        propertyBuilder.add(evaluationTimeout, settings::getEvaluationResponseTimeout, settings::setEvaluationResponseTimeout);
        root.add(LabeledBuilder.simple(PyLocalize.formDebuggerResponseTimeout(), evaluationTimeout));

        return root;
    }
}
