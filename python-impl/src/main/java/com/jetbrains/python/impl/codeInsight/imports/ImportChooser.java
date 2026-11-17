// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.util.concurrent.Promise;

import java.util.List;

@ServiceAPI(ComponentScope.APPLICATION)
public interface ImportChooser {
    static ImportChooser getInstance() {
        return Application.get().getInstance(ImportChooser.class);
    }

    Promise<ImportCandidateHolder> selectImport(List<? extends ImportCandidateHolder> mySources, boolean myUseQualifiedImport);
}
