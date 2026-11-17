// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyNamespacePackageUtil;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.application.util.CachedValueProvider;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PyStubPackages {
    public static final String TYPES_PREFIX = "types-";
    public static final String TYPES_SUFFIX = "-types";
    public static final String STUBS_SUFFIX = "-stubs";

    private static final Key<Boolean> STUB_PACKAGE_KEY = Key.create("PY_STUB_PACKAGE");
    private static final Key<Boolean> INLINE_PACKAGE_KEY = Key.create("PY_INLINE_PACKAGE");

    private PyStubPackages() {
    }

    public static boolean isStubPackage(String name) {
        return name.startsWith(TYPES_PREFIX) || name.endsWith(STUBS_SUFFIX) || name.endsWith(TYPES_SUFFIX);
    }

    public static String stubPackageToPackage(String name) {
        return StringUtil.trimStart(StringUtil.trimEnd(StringUtil.trimEnd(name, STUBS_SUFFIX), TYPES_SUFFIX), TYPES_PREFIX);
    }

    /**
     * If {@code name} argument points to element in stub package,
     * then {@code name} would be copied and `-stubs` suffix would be removed from the first component,
     * otherwise {@code name} would be returned.
     */
    public static QualifiedName convertStubToRuntimePackageName(QualifiedName name) {
        String firstComponent = name.getFirstComponent();
        if (firstComponent == null) {
            return name;
        }
        String top = stubPackageToPackage(firstComponent);

        return QualifiedName.fromComponents(top).append(name.removeHead(1));
    }

    /**
     * Returns stub package directory in the specified {@code dir} for the package with {@code referencedName} as a name.
     * <p>
     * Requires {@code withoutStubs} to be False and {@code dir} to be lib root.
     */
    public static @Nullable Set<PsiDirectory> findStubPackage(PsiDirectory dir,
                                                              String referencedName,
                                                              boolean checkForPackage,
                                                              boolean withoutStubs) {
        VirtualFile dirFile = dir.getVirtualFile();
        if (!withoutStubs && dirFile.equals(getClassOrContentOrSourceRoot(dir.getProject(), dirFile))) {
            String stubPackageName = referencedName + STUBS_SUFFIX;
            PsiDirectory stubPackage = dir.findSubdirectory(stubPackageName);
            Set<PsiDirectory> result = new LinkedHashSet<>();

            // see comment about case sensitivity in com.jetbrains.python.psi.resolve.ResolveImportUtil.resolveInDirectory
            if (stubPackage != null && stubPackageName.equals(stubPackage.getName()) &&
                (!checkForPackage || PyUtil.isPackage(stubPackage, dir))) {
                doTransferStubPackageMarker(stubPackage);
                result.add(stubPackage);
            }

            String typesPackageName = TYPES_PREFIX + referencedName;
            PsiDirectory typesPackage = dir.findSubdirectory(typesPackageName);

            // see comment about case sensitivity in com.jetbrains.python.psi.resolve.ResolveImportUtil.resolveInDirectory
            if (typesPackage != null && typesPackageName.equals(typesPackage.getName()) &&
                (!checkForPackage || PyUtil.isPackage(typesPackage, dir))) {
                doTransferStubPackageMarker(typesPackage);
                result.add(typesPackage);
            }

            String typesSuffixPackageName = referencedName + TYPES_SUFFIX;
            PsiDirectory typesSuffixPackage = dir.findSubdirectory(typesSuffixPackageName);

            // see comment about case sensitivity in com.jetbrains.python.psi.resolve.ResolveImportUtil.resolveInDirectory
            if (typesSuffixPackage != null && typesSuffixPackageName.equals(typesSuffixPackage.getName()) &&
                (!checkForPackage || PyUtil.isPackage(typesSuffixPackage, dir))) {
                doTransferStubPackageMarker(typesSuffixPackage);
                result.add(typesSuffixPackage);
            }
            return result.isEmpty() ? null : result;
        }

        return null;
    }

    /**
     * Puts special mark to module resolved in stub package.
     */
    public static PsiFile transferStubPackageMarker(PsiDirectory dir, PsiFile resolvedSubmodule) {
        if (Boolean.TRUE.equals(dir.getUserData(STUB_PACKAGE_KEY))) {
            resolvedSubmodule.putUserData(STUB_PACKAGE_KEY, true);
        }
        return resolvedSubmodule;
    }

    /**
     * Puts special mark to dir resolved in stub package.
     */
    public static PsiDirectory transferStubPackageMarker(PsiDirectory dir, PsiDirectory resolvedSubdir) {
        if (Boolean.TRUE.equals(dir.getUserData(STUB_PACKAGE_KEY))) {
            doTransferStubPackageMarker(resolvedSubdir);
        }
        return resolvedSubdir;
    }

    private static void doTransferStubPackageMarker(PsiDirectory resolvedSubdir) {
        resolvedSubdir.putUserData(STUB_PACKAGE_KEY, true);
        PsiElement init = PyUtil.turnDirIntoInit(resolvedSubdir);
        if (init != null) {
            init.putUserData(STUB_PACKAGE_KEY, true);
        }
    }

    public static List<RatedResolveResult> removeRuntimeModulesForWhomStubModulesFound(List<RatedResolveResult> resolved) {
        Set<String> stubPkgModules = new LinkedHashSet<>();

        for (RatedResolveResult it : resolved) {
            PsiElement stubPkgModule = it.getElement();
            if (stubPkgModule instanceof PyiFile pyiFile && Boolean.TRUE.equals(pyiFile.getUserData(STUB_PACKAGE_KEY))) {
                stubPkgModules.add(pyiFile.getName());
            }
        }

        if (stubPkgModules.isEmpty()) {
            return resolved;
        }
        List<RatedResolveResult> result = new ArrayList<>();
        for (RatedResolveResult it : resolved) {
            PsiElement runtimePkgModule = it.getElement();
            if (!(runtimePkgModule instanceof PyFile pyFile &&
                !(runtimePkgModule instanceof PyiFile) &&
                stubPkgModules.contains(pyFile.getName() + "i"))) { // py -> pyi
                result.add(it);
            }
        }
        return result;
    }

    private static @Nullable VirtualFile getClassOrContentOrSourceRoot(Project project, VirtualFile file) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);

        VirtualFile classRoot = index.getClassRootForFile(file);
        if (classRoot != null) {
            return classRoot;
        }
        VirtualFile sourceRoot = index.getSourceRootForFile(file);
        if (sourceRoot != null) {
            return sourceRoot;
        }
        VirtualFile contentRoot = index.getContentRootForFile(file);
        if (contentRoot != null) {
            return contentRoot;
        }

        return null;
    }

    /**
     * See {@link #findStubPackage} and {@link #transferStubPackageMarker}.
     */
    public static boolean isInStubPackage(PsiElement element) {
        return Boolean.TRUE.equals(element.getUserData(STUB_PACKAGE_KEY));
    }

    public static boolean isInPartialStubPackage(PsiElement element) {
        // The enclosing stub package (the element itself, its package directory, or any ancestor up to the root) is explicitly
        // marked as partial via its `py.typed` marker. Works both for package directories and for plain stub modules
        // (e.g. `foo-stubs/bar.pyi`), so that submodules of a partial stub package still fall back to the runtime package.
        VirtualFile pyTyped = getPyTyped(element);
        if (pyTyped == null) {
            return false;
        }
        return isPartialPyTypedMarker(element.getProject(), pyTyped);
    }

    /**
     * Returns `true` if the stub package {@code element} directly contains a sub-package named {@code name} that is a namespace package.
     * <p>
     * Namespace packages cannot ship complete type information (PEP 561), so submodules resolved underneath them must still
     * fall back to the runtime package even when the enclosing stub package itself is complete.
     */
    public static boolean hasNamespaceSubPackage(PsiElement element, String name) {
        PsiDirectory packageDir = getPackageDir(element);
        if (packageDir == null) {
            return false;
        }
        PsiDirectory subPackage = packageDir.findSubdirectory(name);
        if (subPackage == null) {
            return false;
        }
        return PyNamespacePackageUtil.isNamespacePackage(subPackage);
    }

    /**
     * See <a href="https://www.python.org/dev/peps/pep-0561/#packaging-type-information">PEP 561</a>.
     * Value is cached in element's user data.
     */
    public static boolean isInInlinePackage(PsiElement element, @Nullable Module module) {
        if (module == null) {
            return false;
        }

        Boolean cached = element.getUserData(INLINE_PACKAGE_KEY);
        if (cached != null) {
            return cached;
        }

        boolean result = !PyiUtil.isPyiFileOfPackage(element) &&
            (element instanceof PyFile || PyUtil.turnDirIntoInit(element) instanceof PyFile) &&
            getPyTyped(element) != null;

        element.putUserData(INLINE_PACKAGE_KEY, result);
        return result;
    }

    /**
     * See <a href="https://www.python.org/dev/peps/pep-0561/#packaging-type-information">PEP 561</a>
     */
    private static @Nullable VirtualFile getPyTyped(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        VirtualFile file;
        if (element instanceof PsiFileSystemItem fileSystemItem) {
            file = fileSystemItem.getVirtualFile();
        }
        else {
            PsiFile containingFile = element.getContainingFile();
            file = containingFile != null ? containingFile.getVirtualFile() : null;
        }
        if (file == null) {
            return null;
        }

        VirtualFile root = getClassOrContentOrSourceRoot(element.getProject(), file);
        if (root == null) {
            return null;
        }
        VirtualFile current = file.isDirectory() ? file : file.getParent();

        while (current != null && current.isDirectory()) {
            VirtualFile pyTyped = current.findChild("py.typed");
            if (pyTyped != null && !pyTyped.isDirectory()) {
                return pyTyped;
            }

            if (current.equals(root)) {
                break;
            }
            current = current.getParent();
        }

        return null;
    }

    private static @Nullable PsiDirectory getPackageDir(PsiElement element) {
        return PyUtil.turnInitIntoDir(element) instanceof PsiDirectory packageDir ? packageDir : null;
    }

    private static boolean isPartialPyTypedMarker(Project project, VirtualFile pyTyped) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(pyTyped);
        if (psiFile == null) {
            return false;
        }
        return LanguageCachedValueUtil.getCachedValue(psiFile, () -> CachedValueProvider.Result.create(readPyTypedMarker(pyTyped), psiFile));
    }

    private static boolean readPyTypedMarker(VirtualFile pyTyped) {
        try {
            Document document = FileDocumentManager.getInstance().getCachedDocument(pyTyped);
            String text = document != null ? document.getText() : VirtualFileUtil.loadText(pyTyped);
            return text.contains("partial\n");
        }
        catch (Throwable e) {
            return false;
        }
    }
}
