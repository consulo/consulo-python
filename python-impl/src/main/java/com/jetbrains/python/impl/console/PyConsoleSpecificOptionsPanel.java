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

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PyCommonOptionsFormData;
import com.jetbrains.python.impl.run.PyCommonOptionsFormFactory;
import consulo.codeEditor.EditorEx;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiDocumentManager;
import consulo.module.Module;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.awt.Dimension;
import java.util.List;
import java.util.Objects;

/**
 * JB builds this panel with the Kotlin UI DSL. Consulo builds it with the unified UI.
 * <p>
 * Consulo-specific: the panel can be created more than once (the configurable recreates it), so the
 * SDK-table subscription disposable is recreated on every {@link #createPanel} call, and
 * {@link PyCommonOptionsFormFactory#createForm} takes it directly.
 */
final class PyConsoleSpecificOptionsPanel {
    private final Project myProject;
    private PyConsoleOptions.@Nullable PyConsoleSettings myConsoleSettings;
    private @Nullable EditorTextField myEditorTextField;
    private @Nullable AbstractPyCommonOptionsForm myCommonOptionsForm;

    // Owns the SDK-table subscription set up by `myCommonOptionsForm.subscribe`; disposed in `disposeUIResources`.
    private @Nullable Disposable myUiDisposable;

    PyConsoleSpecificOptionsPanel(Project project) {
        myProject = project;
    }

    @RequiredUIAccess
    public Component createPanel(PyConsoleOptions.PyConsoleSettings optionsProvider) {
        disposeUIResources();

        Disposable uiDisposable = Disposable.newDisposable();
        myUiDisposable = uiDisposable;

        myConsoleSettings = optionsProvider;
        AbstractPyCommonOptionsForm commonOptionsForm = myProject.getApplication()
            .getInstance(PyCommonOptionsFormFactory.class)
            .createForm(createCommonOptionsFormData(myProject), uiDisposable);
        commonOptionsForm.subscribe();
        myCommonOptionsForm = commonOptionsForm;
        myEditorTextField = createEditorTextField(optionsProvider.myCustomStartScript);

        return createPanel();
    }

    public void apply() {
        PyConsoleOptions.PyConsoleSettings consoleSettings = Objects.requireNonNull(myConsoleSettings);
        consoleSettings.myCustomStartScript = Objects.requireNonNull(myEditorTextField).getText();
        consoleSettings.apply(Objects.requireNonNull(myCommonOptionsForm));
    }

    public boolean isModified() {
        PyConsoleOptions.PyConsoleSettings consoleSettings = Objects.requireNonNull(myConsoleSettings);
        return !Objects.requireNonNull(myEditorTextField).getText().equals(consoleSettings.myCustomStartScript)
            || consoleSettings.isModified(Objects.requireNonNull(myCommonOptionsForm));
    }

    public void reset() {
        PyConsoleOptions.PyConsoleSettings consoleSettings = Objects.requireNonNull(myConsoleSettings);
        EditorTextField editorTextField = Objects.requireNonNull(myEditorTextField);
        UIUtil.invokeLaterIfNeeded(() -> editorTextField.setText(consoleSettings.myCustomStartScript));
        consoleSettings.reset(myProject, Objects.requireNonNull(myCommonOptionsForm));
    }

    public void disposeUIResources() {
        Disposable uiDisposable = myUiDisposable;
        if (uiDisposable != null) {
            myUiDisposable = null;
            Disposer.dispose(uiDisposable);
        }
    }

    @RequiredUIAccess
    private Component createPanel() {
        EditorTextField editorTextField = Objects.requireNonNull(myEditorTextField);
        editorTextField.setMinimumSize(new Dimension(80, 80));
        editorTextField.setPreferredSize(new Dimension(100, 130));

        VerticalLayout panel = VerticalLayout.create();
        panel.add(Objects.requireNonNull(myCommonOptionsForm).getMainPanel());
        panel.add(LabeledLayout.create(PyLocalize.formConsoleSpecificOptionsStartingScript(), TargetAWT.wrap(editorTextField)));
        return panel;
    }

    private static PyCommonOptionsFormData createCommonOptionsFormData(Project project) {
        return new PyCommonOptionsFormData() {
            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public List<Module> getValidModules() {
                return AbstractPythonRunConfiguration.getValidModules(project);
            }

            @Override
            public boolean showConfigureInterpretersLink() {
                return true;
            }
        };
    }

    private EditorTextField createEditorTextField(String text) {
        return new EditorTextField(createDocument(myProject, text), myProject, PythonFileType.INSTANCE) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                editor.setVerticalScrollbarVisible(true);
                return editor;
            }

            @Override
            public boolean isOneLineMode() {
                return false;
            }
        };
    }

    private static Document createDocument(Project project, String text) {
        PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "start_script.py", text.trim(), true);
        return Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(fragment));
    }
}
