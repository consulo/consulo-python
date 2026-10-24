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
package com.jetbrains.python.impl.testing.pytest;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;

public class PyTestKeywordsFragment implements RunConfigurationFragment<PyTestRunConfiguration> {
    @Override
    public String getId() {
        return "pyTestKeywords";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentPytestKeywords();
    }

    @Override
    public boolean isVisibleByDefault(PyTestRunConfiguration target) {
        return target.useKeyword();
    }

    @Override
    public void applyHidden(PyTestRunConfiguration target) {
        target.useKeyword(false);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<PyTestRunConfiguration> createEditor(Project project, Disposable parent) {
        TextBox keywords = TextBox.create();
        keywords.setToolTipText(PyLocalize.runcfgPytestKeywordsTooltip());

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(PyLocalize.runcfgPytestKeywords(), keywords);
            }

            @RequiredUIAccess
            @Override
            public void reset(PyTestRunConfiguration target) {
                keywords.setValue(StringUtil.notNullize(target.getOptions().getKeywords()), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(PyTestRunConfiguration target) {
                target.useKeyword(true);
                target.setKeywords(StringUtil.notNullize(keywords.getValue()).trim());
            }
        };
    }
}
