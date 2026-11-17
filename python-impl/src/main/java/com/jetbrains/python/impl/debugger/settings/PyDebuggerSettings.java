// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.settings;

import com.jetbrains.python.debugger.QuotingPolicy;
import com.jetbrains.python.debugger.SortingPolicy;
import com.jetbrains.python.debugger.ValuesPolicy;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.IdeaSimpleConfigurable;
import consulo.execution.debug.setting.DebuggerSettingsCategory;
import consulo.execution.debug.setting.XDebuggerSettings;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.SmartList;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;

@ExtensionImpl
public final class PyDebuggerSettings extends XDebuggerSettings<PyDebuggerSettings> implements Supplier<PyDebuggerSettings> {
    private boolean myLibrariesFilterEnabled;
    private boolean mySteppingFiltersEnabled;
    private List<PySteppingFilter> mySteppingFilters;
    public static final String FILTERS_DIVIDER = ";";
    private boolean myWatchReturnValues = false;
    private boolean mySimplifiedView = true;
    private volatile ValuesPolicy myValuesPolicy = ValuesPolicy.ASYNC;
    private volatile QuotingPolicy myQuotingPolicy = QuotingPolicy.SINGLE;
    private volatile SortingPolicy mySortingPolicy = SortingPolicy.DO_NOT_SORT;
    private boolean myAlwaysDoSmartStepIntoEnabled = true;
    private boolean myDebugpyLoggingEnabled = false;

    public PyDebuggerSettings() {
        super("python");
        mySteppingFilters = new SmartList<>();
    }

    public boolean isWatchReturnValues() {
        return myWatchReturnValues;
    }

    public void setWatchReturnValues(boolean watchReturnValues) {
        myWatchReturnValues = watchReturnValues;
    }

    public boolean isSimplifiedView() {
        return mySimplifiedView;
    }

    public void setSimplifiedView(boolean simplifiedView) {
        mySimplifiedView = simplifiedView;
    }

    public ValuesPolicy getValuesPolicy() {
        return myValuesPolicy;
    }

    public void setValuesPolicy(ValuesPolicy valuesPolicy) {
        myValuesPolicy = valuesPolicy;
    }

    public QuotingPolicy getQuotingPolicy() {
        return myQuotingPolicy;
    }

    public void setQuotingPolicy(QuotingPolicy copyQuotingPolicy) {
        myQuotingPolicy = copyQuotingPolicy;
    }

    public SortingPolicy getSortingPolicy() {
        return mySortingPolicy;
    }

    public void setSortingPolicy(SortingPolicy sortingPolicy) {
        mySortingPolicy = sortingPolicy;
    }

    public static PyDebuggerSettings getInstance() {
        return getInstance(PyDebuggerSettings.class);
    }

    public boolean isLibrariesFilterEnabled() {
        return myLibrariesFilterEnabled;
    }

    public void setLibrariesFilterEnabled(boolean librariesFilterEnabled) {
        myLibrariesFilterEnabled = librariesFilterEnabled;
    }

    public boolean isSteppingFiltersEnabled() {
        return mySteppingFiltersEnabled;
    }

    public void setSteppingFiltersEnabled(boolean steppingFiltersEnabled) {
        mySteppingFiltersEnabled = steppingFiltersEnabled;
    }

    public void setAlwaysDoSmartStepIntoEnabled(boolean alwaysDoSmartStepIntoEnabled) {
        myAlwaysDoSmartStepIntoEnabled = alwaysDoSmartStepIntoEnabled;
    }

    public boolean isAlwaysDoSmartStepInto() {
        return myAlwaysDoSmartStepIntoEnabled;
    }

    public boolean isDebugpyLoggingEnabled() {
        return myDebugpyLoggingEnabled;
    }

    public void setDebugpyLoggingEnabled(boolean enabled) {
        myDebugpyLoggingEnabled = enabled;
    }

    public List<PySteppingFilter> getSteppingFilters() {
        return mySteppingFilters;
    }

    public String getSteppingFiltersForProject(Project project) {
        StringBuilder sb = new StringBuilder();
        for (PySteppingFilter filter : mySteppingFilters) {
            if (filter.isEnabled()) {
                sb.append(filter.getAbsolutePlatformIndependentFilter(project)).append(FILTERS_DIVIDER);
            }
        }
        return sb.toString();
    }

    public void setSteppingFilters(List<PySteppingFilter> steppingFilters) {
        mySteppingFilters = steppingFilters;
    }

    @Override
    public PyDebuggerSettings getState() {
        return this;
    }

    @Override
    public void loadState(PyDebuggerSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // TODO consulo: XDebuggerSettings.isTargetedToProduct missing
    @Override
    public Collection<? extends Configurable> createConfigurables(DebuggerSettingsCategory category) {
        if (category == DebuggerSettingsCategory.STEPPING) {
            return singletonList(IdeaSimpleConfigurable.create("python.debug.configurable", PyLocalize.python(),
                                                               PyDebuggerSteppingConfigurableUi.class, this));
        }
        return Collections.emptyList();
    }

    @Override
    public PyDebuggerSettings get() {
        return this;
    }
}
