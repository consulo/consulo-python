// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.settings;

import consulo.project.Project;
import consulo.util.io.FileUtil;

public class PySteppingFilter {
    private boolean myIsEnabled;
    private String myFilter;

    public PySteppingFilter() {
        myIsEnabled = true;
        myFilter = "";
    }

    public PySteppingFilter(boolean isEnabled, String filter) {
        myIsEnabled = isEnabled;
        myFilter = filter;
    }

    public boolean isEnabled() {
        return myIsEnabled;
    }

    public void setEnabled(boolean enabled) {
        myIsEnabled = enabled;
    }

    public String getFilter() {
        return myFilter;
    }

    public String getAbsolutePlatformIndependentFilter(Project project) {
        StringBuilder resultFilter = new StringBuilder();
        String[] filters = myFilter.split(PyDebuggerSettings.FILTERS_DIVIDER);
        for (String filter : filters) {
            if (!(FileUtil.isAbsolutePlatformIndependent(filter) || filter.startsWith("*"))) {
                resultFilter.append(project.getBasePath()).append('/');
            }
            resultFilter.append(filter).append(PyDebuggerSettings.FILTERS_DIVIDER);
        }
        return resultFilter.toString().replace('\\', '/');
    }

    public void setFilter(String filter) {
        myFilter = filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PySteppingFilter filter)) {
            return false;
        }

        if (isEnabled() != filter.isEnabled()) {
            return false;
        }
        if (!getFilter().equals(filter.getFilter())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = (isEnabled() ? 1 : 0);
        result = 31 * result + getFilter().hashCode();
        return result;
    }
}
