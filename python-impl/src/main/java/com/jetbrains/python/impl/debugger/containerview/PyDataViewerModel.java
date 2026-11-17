// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public class PyDataViewerModel {
    private final Project myProject;
    private final PyFrameAccessor myFrameAccessor;

    /**
     * Represents a formatting string used for specifying format.
     * <p>
     * This field is used as a source of truth during switching between community and powerful tables.
     */
    private String myFormat = "";

    /**
     * Represents a slicing string used for slicing data in a viewer panel.
     * For example, np_array_3d[0] or df['column_1'].
     * <p>
     * This field is used as a source of truth during switching between community and powerful tables.
     */
    private String mySlicing = "";

    /**
     * This field is used as a source of truth during switching between community and powerful tables.
     */
    private boolean myIsColored = false;

    private boolean myProtectedColored;

    private @Nullable String myOriginalVarName = null;

    private @Nullable String myModifiedVarName = null;

    private @Nullable PyDebugValue myDebugValue = null;

    private boolean myIsModified = false;

    public PyDataViewerModel(Project project, PyFrameAccessor frameAccessor) {
        myProject = project;
        myFrameAccessor = frameAccessor;
        myProtectedColored = PyDataView.isColoringEnabled(project);
    }

    public Project getProject() {
        return myProject;
    }

    public PyFrameAccessor getFrameAccessor() {
        return myFrameAccessor;
    }

    public String getFormat() {
        return myFormat;
    }

    public void setFormat(String format) {
        myFormat = format;
    }

    public String getSlicing() {
        return mySlicing;
    }

    public void setSlicing(String slicing) {
        mySlicing = slicing;
    }

    public boolean isColored() {
        return myIsColored;
    }

    public void setColored(boolean colored) {
        myIsColored = colored;
    }

    public boolean getProtectedColored() {
        return myProtectedColored;
    }

    public void setProtectedColored(boolean protectedColored) {
        myProtectedColored = protectedColored;
    }

    public @Nullable String getOriginalVarName() {
        return myOriginalVarName;
    }

    public void setOriginalVarName(@Nullable String originalVarName) {
        myOriginalVarName = originalVarName;
    }

    public @Nullable String getModifiedVarName() {
        return myModifiedVarName;
    }

    public void setModifiedVarName(@Nullable String modifiedVarName) {
        myModifiedVarName = modifiedVarName;
    }

    public @Nullable PyDebugValue getDebugValue() {
        return myDebugValue;
    }

    public void setDebugValue(@Nullable PyDebugValue debugValue) {
        myDebugValue = debugValue;
    }

    public boolean isModified() {
        return myIsModified;
    }

    public void setModified(boolean modified) {
        myIsModified = modified;
    }
}
