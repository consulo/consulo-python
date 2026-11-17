// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.find.FindUsagesOptions;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * @author Yuli Fiterman
 */
@State(name = "PyFindUsagesOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyFindUsagesOptions extends FindUsagesOptions implements PersistentStateComponent<PyFindUsagesOptions.State> {


    @Inject
    public PyFindUsagesOptions(Project project) {
        super(project);
        isUsages = true;
        isSearchForTextOccurrences = false;
    }

    public static PyFindUsagesOptions getInstance(Project project) {
        return project.getInstance(PyFindUsagesOptions.class);
    }


    @Override
    public @Nullable State getState() {
        State s = new State();
        s.isSearchForTextOccurrences = isSearchForTextOccurrences;
        return s;
    }

    @Override
    public void loadState(State state) {
        isSearchForTextOccurrences = state.isSearchForTextOccurrences;
    }

    static class State {
        public boolean isSearchForTextOccurrences;
    }
}
