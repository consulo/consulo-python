/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Context for resolving Python qualified names with various options.
 *
 */
public interface PyQualifiedNameResolveContext {
    @Nullable
    PsiElement getFoothold();

    int getRelativeLevel();

    @Nullable
    Module getModule();

    Project getProject();

    boolean getWithoutRoots();

    boolean getWithoutForeign();

    boolean getWithoutStubs();

    PsiManager getPsiManager();

    boolean getWithMembers();

    boolean getWithPlainDirectories();

    boolean getVisitAllModules();

    boolean isValid();

    @Nullable
    PsiFile getFootholdFile();

    @Nullable
    PsiDirectory getContainingDirectory();

    PyQualifiedNameResolveContext copyWithoutForeign();

    PyQualifiedNameResolveContext copyWithMembers();

    PyQualifiedNameResolveContext copyWithPlainDirectories();

    PyQualifiedNameResolveContext copyWithRelative(int relativeLevel);

    PyQualifiedNameResolveContext copyWithoutRoots();

    PyQualifiedNameResolveContext copyWithRoots();

    PyQualifiedNameResolveContext copyWithoutStubs();
}
