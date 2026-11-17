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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.python.module.extension.PyModuleExtension;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The language level of each Python module and of each interpreter, as one snapshot of {@code EvoPyProjectModel} states them.
 * {@link PythonLanguageLevelPusher} pushes the level of a module to its source roots, and the level of an interpreter to its
 * class roots. Two equal layouts push the same levels, so the pusher compares layouts to skip a push.
 * <p>
 * A module without an interpreter is not here, and its files get the level of the project, see {@link #getProjectLevel()}.
 * <p>
 * Consulo: {@code PythonInterpreter} is missing, an interpreter is its Python {@link Sdk}.
 */
public final class PythonLanguageLevelLayout {
    /**
     * The layout before the first snapshot, and of a project without Python modules.
     */
    public static final PythonLanguageLevelLayout EMPTY =
        new PythonLanguageLevelLayout(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final Map<Module, LanguageLevel> moduleLevels;
    private final Map<Sdk, LanguageLevel> interpreterLevels;
    /**
     * The level of each class root of an interpreter, for a file in no module, see {@link #levelOfInterpreterFile}.
     */
    private final Map<VirtualFile, LanguageLevel> rootLevels;

    PythonLanguageLevelLayout(Map<Module, LanguageLevel> moduleLevels,
                              Map<Sdk, LanguageLevel> interpreterLevels,
                              Map<VirtualFile, LanguageLevel> rootLevels) {
        this.moduleLevels = moduleLevels;
        this.interpreterLevels = interpreterLevels;
        this.rootLevels = rootLevels;
    }

    public Map<Module, LanguageLevel> getModuleLevels() {
        return moduleLevels;
    }

    public Map<Sdk, LanguageLevel> getInterpreterLevels() {
        return interpreterLevels;
    }

    /**
     * The level of a file that belongs to no interpreter: the highest level of a module, or the default level.
     */
    public LanguageLevel getProjectLevel() {
        LanguageLevel max = null;
        for (LanguageLevel level : moduleLevels.values()) {
            if (max == null || LanguageLevel.VERSION_COMPARATOR.compare(level, max) > 0) {
                max = level;
            }
        }
        return max != null ? max : LanguageLevel.getDefault();
    }

    /**
     * The level of the interpreter whose class root holds {@code file}, or {@code null} when no class root holds it.
     */
    public @Nullable LanguageLevel levelOfInterpreterFile(VirtualFile file) {
        for (VirtualFile current = file; current != null; current = current.getParent()) {
            LanguageLevel level = rootLevels.get(current);
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    /**
     * The layout of {@code snapshot}. An interpreter whose SDK is disposed has no roots and no version, so it is not here.
     * <p>
     * Consulo: {@code EvoPyProjectModel.Snapshot} is missing, the snapshot is the Python modules of {@code project} and
     * their SDKs.
     */
    public static PythonLanguageLevelLayout of(Project project) {
        // TODO consulo: EvoPyProjectModel.Snapshot missing - its interpreters are the SDKs of the Python modules
        Map<Module, Sdk> moduleInterpreters = new LinkedHashMap<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed() || ModuleUtilCore.getExtension(module, PyModuleExtension.class) == null) {
                continue;
            }
            Sdk sdk = PythonSdkType.findPythonSdk(module);
            if (sdk != null) {
                moduleInterpreters.put(module, sdk);
            }
        }

        // TODO consulo: PythonInterpreter.isDisposed missing
        Map<Sdk, LanguageLevel> interpreterLevels = new LinkedHashMap<>();
        for (Sdk interpreter : moduleInterpreters.values()) {
            interpreterLevels.computeIfAbsent(interpreter, PythonSdkType::getLanguageLevelForSdk);
        }
        Map<Module, LanguageLevel> moduleLevels = new LinkedHashMap<>();
        for (Map.Entry<Module, Sdk> entry : moduleInterpreters.entrySet()) {
            LanguageLevel level = interpreterLevels.get(entry.getValue());
            if (level != null) {
                moduleLevels.put(entry.getKey(), level);
            }
        }
        Map<VirtualFile, LanguageLevel> rootLevels = new HashMap<>();
        for (Map.Entry<Sdk, LanguageLevel> entry : interpreterLevels.entrySet()) {
            for (VirtualFile root : classRoots(entry.getKey())) {
                rootLevels.putIfAbsent(root, entry.getValue());
            }
        }
        return new PythonLanguageLevelLayout(moduleLevels, interpreterLevels, rootLevels);
    }

    /**
     * Consulo: {@code PythonInterpreter.classRoots(project)} is missing, the class roots are the binaries roots of the SDK.
     */
    static VirtualFile[] classRoots(Sdk interpreter) {
        return interpreter.getRootProvider().getFiles(BinariesOrderRootType.ID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PythonLanguageLevelLayout that)) {
            return false;
        }
        return moduleLevels.equals(that.moduleLevels) &&
            interpreterLevels.equals(that.interpreterLevels) &&
            rootLevels.equals(that.rootLevels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleLevels, interpreterLevels, rootLevels);
    }

    @Override
    public String toString() {
        return "PythonLanguageLevelLayout(moduleLevels=" + moduleLevels +
            ", interpreterLevels=" + interpreterLevels +
            ", rootLevels=" + rootLevels + ")";
    }
}
