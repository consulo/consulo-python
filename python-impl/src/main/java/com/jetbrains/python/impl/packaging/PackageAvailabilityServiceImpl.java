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
package com.jetbrains.python.impl.packaging;

import com.jetbrains.python.impl.psi.resolve.PackageAvailabilityService;
import com.jetbrains.python.impl.psi.resolve.PackageAvailabilitySpec;
import com.jetbrains.python.impl.psi.resolve.PyResolveCachesInvalidator;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Implementation of {@link PackageAvailabilityService} that uses {@code PythonPackageManager}
 * for fast package lookups with PSI resolution fallback.
 */
@ServiceImpl
@Singleton
public class PackageAvailabilityServiceImpl implements PackageAvailabilityService {
    /**
     * Checks if any of the fully-qualified names can be resolved to a real .py file (not .pyi stub)
     * in the library paths of {@code interpreter}.
     * <p>
     * This function performs PSI resolution and checks that the resolved element:
     * 1. Is not a .pyi stub file
     * 2. Is located in a library directory
     * <p>
     * The resolution skips the stub roots, such as the typeshed: resolve gives only the top result, and a stub of an
     * installed package ranks above its {@code .py} file.
     *
     * @param project     the project context
     * @param interpreter the interpreter to resolve against
     * @param fqns        the fully-qualified names to resolve (e.g., "pytest.fixture")
     * @return true if any FQN resolves to a .py file in library, false otherwise
     */
    public static boolean canResolveFqn(Project project, Sdk interpreter, List<String> fqns) {
        PyQualifiedNameResolveContext context = PyResolveImportUtil.fromInterpreter(project, interpreter).copyWithMembers().copyWithoutStubs();
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

        if (Application.get().isReadAccessAllowed()) {
            return compute(context, fileIndex, fqns);
        }
        return ReadAction.compute(() -> compute(context, fileIndex, fqns));
    }

    private static boolean compute(PyQualifiedNameResolveContext context, ProjectFileIndex fileIndex, List<String> fqns) {
        return fqns.stream().anyMatch(it -> canResolveSingleFqn(context, fileIndex, it));
    }

    private static boolean canResolveSingleFqn(PyQualifiedNameResolveContext context, ProjectFileIndex fileIndex, String fqn) {
        QualifiedName qName = QualifiedName.fromDottedString(fqn);
        return PyResolveImportUtil.resolveQualifiedName(qName, context).stream().anyMatch(element -> {
            PsiFile file = element.getContainingFile();
            if (file == null) {
                return false;
            }
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                return false;
            }
            return !(file instanceof PyiFile) && fileIndex.isInLibrary(virtualFile);
        });
    }

    public PackageAvailabilityServiceImpl() {
        // TODO consulo: PythonSdkTable.pythonSdksOfEveryTier and UserDataHolderBase.getUserMap missing - JB logic:
        //  messageBus.connect(cs).subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        //      @Override
        //      public void projectClosed(Project project) {
        //          for (Sdk sdk : PythonSdkTable.pythonSdksOfEveryTier()) {
        //              for (Key<?> key : ((UserDataHolderBase) sdk).getUserMap().getKeys()) {
        //                  if (PackageAvailabilityKt.isPackageAvailableKey(key)) {
        //                      sdk.putUserData(key, null);
        //                  }
        //              }
        //          }
        //      }
        //  });
    }

    @Override
    public boolean isPackageAvailable(Module module, PackageAvailabilitySpec spec) {
        // TODO consulo: findModuleInterpreterIfReady missing - PythonSdkType.findPythonSdk is used instead
        Sdk interpreter = PythonSdkType.findPythonSdk(module);
        if (interpreter == null) {
            return false;
        }

        // 1. First check package manager (has its own snapshot cache)
        if (isPackageInstalledInPackageManager(module.getProject(), interpreter, spec.getPackageName())) {
            return true;
        }

        // 2. Fallback to cached PSI resolution
        return canResolveFqnCached(module, spec);
    }

    @Override
    public boolean isPackageAvailable(Project project, Sdk sdk, PackageAvailabilitySpec spec) {
        // Sync, so it does not wait. An SDK that this project holds no interpreter for has no packages to look at.
        // TODO consulo: Project.findPythonInterpreterIfReady missing - the sdk is the interpreter
        Sdk interpreter = sdk;
        // 1. First check package manager (has its own snapshot cache)
        if (isPackageInstalledInPackageManager(project, interpreter, spec.getPackageName())) {
            return true;
        }

        // 2. Fallback to cached PSI resolution
        return canResolveFqnCached(project, sdk, interpreter, spec);
    }

    @Override
    public @Nullable String installedPackageVersion(Project project, Sdk interpreter, String packageName) {
        PyPackageName normalizedName = PyPackageName.from(packageName);
        for (PyPackage pkg : listInstalledPackagesSnapshot(interpreter)) {
            if (PyPackageName.from(pkg.getName()).equals(normalizedName)) {
                return pkg.getVersion();
            }
        }
        return null;
    }

    private boolean isPackageInstalledInPackageManager(Project project, Sdk interpreter, String packageName) {
        PyPackageName normalizedName = PyPackageName.from(packageName);
        return listInstalledPackagesSnapshot(interpreter).stream()
            .anyMatch(it -> PyPackageName.from(it.getName()).equals(normalizedName));
    }

    private static List<PyPackage> listInstalledPackagesSnapshot(Sdk interpreter) {
        // TODO consulo: PythonPackageManager.forPythonInterpreter(project, interpreter).listInstalledPackagesSnapshot() missing -
        //  PyPackageManager.getPackages() (the cached list) is used instead
        List<PyPackage> packages = PyPackageManager.getInstance(interpreter).getPackages();
        return packages != null ? packages : List.of();
    }

    private boolean canResolveFqnCached(Module module, PackageAvailabilitySpec spec) {
        return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, spec.getCacheKey(), () -> {
            // TODO consulo: findModuleInterpreterIfReady missing - PythonSdkType.findPythonSdk is used instead
            Sdk interpreter = PythonSdkType.findPythonSdk(module);
            return CachedValueProvider.Result.create(
                interpreter != null && canResolveFqn(module.getProject(), interpreter, spec.getFqns()),
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                // The interpreter comes from the snapshot, so a snapshot that changes an interpreter computes the value again.
                PyResolveCachesInvalidator.getInstance(module.getProject()).getModificationTracker()
            );
        }, false);
    }

    /**
     * The value is stored on {@code sdk}, see {@link PackageAvailabilityServiceImpl} that drops it when the project closes.
     */
    private boolean canResolveFqnCached(Project project, Sdk sdk, Sdk interpreter, PackageAvailabilitySpec spec) {
        if (Application.get().isUnitTestMode()) {
            return canResolveFqn(project, interpreter, spec.getFqns());
        }
        return CachedValuesManager.getManager(project).getCachedValue(sdk, spec.getCacheKey(), () -> {
            return CachedValueProvider.Result.create(
                canResolveFqn(project, interpreter, spec.getFqns()),
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
            );
        }, false);
    }
}
