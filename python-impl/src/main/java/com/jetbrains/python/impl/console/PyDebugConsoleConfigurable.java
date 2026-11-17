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
import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.codeEditor.EditorEx;
import consulo.configurable.Configurable;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.document.Document;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiDocumentManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.awt.Dimension;
import java.util.Objects;

/**
 * The page owns the Debug Console only. Every value it edits is separate from the Python Console one, so a
 * change here never alters the Python Console page.
 * <p>
 * JB builds this page with the Kotlin UI DSL. Consulo builds it with the unified UI.
 */
@ExtensionImpl
public class PyDebugConsoleConfigurable extends SimpleConfigurableByProperties implements Configurable.NoScroll, ProjectConfigurable {
    public static final String ID = "python.debug.console";

    private final Project myProject;

    @Inject
    public PyDebugConsoleConfigurable(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public @Nullable String getParentId() {
        // JB: groupId="reference.idesettings.debugger.python", the id of PyDebuggerConfigurable
        return "reference.idesettings.debugger.python";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.configurablePydebugconsoleconfigurableDisplayName();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        PyConsoleOptions consoleOptions = PyConsoleOptions.getInstance(myProject);
        PyDebuggerOptionsProvider debuggerOptions = PyDebuggerOptionsProvider.getInstance(myProject);
        EditorTextField editor = createStartScriptEditor(debuggerOptions.getDebugConsoleStartScript());

        VerticalLayout root = VerticalLayout.create();

        // 'Debug Console' is a name and properly capitalized.
        CheckBox showDebugConsole = CheckBox.create(PyLocalize.formConsoleOptionsAlwaysShowDebugConsole());
        propertyBuilder.add(showDebugConsole, consoleOptions::isShowDebugConsoleByDefault, consoleOptions::setShowDebugConsoleByDefault);
        root.add(showDebugConsole);

        CheckBox ipythonEnabled = CheckBox.create(PyLocalize.formConsoleOptionsUseIpythonIfAvailable());
        propertyBuilder.add(
            ipythonEnabled,
            debuggerOptions::isDebugConsoleIpythonEnabled,
            debuggerOptions::setDebugConsoleIpythonEnabled
        );
        root.add(ipythonEnabled);

        CheckBox commandQueueEnabled = CheckBox.create(PyLocalize.formDebugConsoleOptionsUseCommandQueue());
        propertyBuilder.add(
            commandQueueEnabled,
            debuggerOptions::isDebugConsoleCommandQueueEnabled,
            debuggerOptions::setDebugConsoleCommandQueueEnabled
        );
        root.add(commandQueueEnabled);

        editor.setMinimumSize(new Dimension(80, 80));
        editor.setPreferredSize(new Dimension(100, 130));
        propertyBuilder.add(
            editor::getText,
            editor::setText,
            debuggerOptions::getDebugConsoleStartScript,
            debuggerOptions::setDebugConsoleStartScript
        );
        root.add(LabeledLayout.create(PyLocalize.formConsoleSpecificOptionsStartingScript(), TargetAWT.wrap(editor)));
        return root;
    }

    private EditorTextField createStartScriptEditor(String text) {
        return new EditorTextField(createDocument(text), myProject, PythonFileType.INSTANCE) {
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

    private Document createDocument(String text) {
        return ReadAction.compute(() -> {
            PyExpressionCodeFragmentImpl fragment =
                new PyExpressionCodeFragmentImpl(myProject, "debug_console_start_script.py", text.trim(), true);
            return Objects.requireNonNull(PsiDocumentManager.getInstance(myProject).getDocument(fragment));
        });
    }
}
