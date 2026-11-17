// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PyTypeRepresentationResolveKt {
    private static final String UNKNOWN_MODULE = "__unknown__";

    private PyTypeRepresentationResolveKt() {
    }

    /**
     * Resolves a qualified name from a type representation, first from the import roots, then by the type engine's module name.
     * A result that is not a {@code T} does not stop the fallback.
     */
    public static <T extends PsiElement> @Nullable T resolveTypeRepresentationName(QualifiedName qualifiedName,
                                                                                   PsiElement anchor,
                                                                                   TypeEvalContext context,
                                                                                   Class<T> cls) {
        PsiElement resolved = PyResolveUtil.resolveFullyQualifiedName(qualifiedName, anchor, context);
        if (cls.isInstance(resolved)) {
            return cls.cast(resolved);
        }
        PsiFile contextFile = FileContextUtil.getContextFile(anchor);
        PsiElement byModuleName = contextFile != null ? resolveByTypeEngineModuleName(qualifiedName, contextFile, context) : null;
        return cls.isInstance(byModuleName) ? cls.cast(byModuleName) : null;
    }

    /**
     * Resolves {@code qualifiedName} in the project file whose path ends with its module part.
     * <p>
     * A type engine names a module by its own import roots, which need not match the project's: Pyrefly with no config names {@code src/sample.py}
     * {@code sample}, and a file off its search path {@code __unknown__}, which can only be {@code contextFile} itself. Among several files that match, the one
     * the type was requested for wins, then one in its directory; otherwise the match must be unique.
     */
    public static @Nullable PsiElement resolveByTypeEngineModuleName(QualifiedName qualifiedName, PsiFile contextFile, TypeEvalContext context) {
        // The original of a completion or preview copy, so that its PSI does not end up among the types of the physical file
        PyFile file = contextFile.getOriginalFile() instanceof PyFile pyFile && !(pyFile instanceof PyTypeRepresentationFile) ? pyFile : null;
        if (UNKNOWN_MODULE.equals(qualifiedName.getFirstComponent())) {
            return file != null ? PyResolveUtil.resolveMemberPath(file, qualifiedName.removeHead(1), context) : null;
        }
        for (int moduleLength = qualifiedName.getComponentCount() - 1; moduleLength >= 1; moduleLength--) {
            PyFile module = findModule(qualifiedName.subQualifiedName(0, moduleLength), file, contextFile.getProject());
            PsiElement result = module != null ? PyResolveUtil.resolveMemberPath(module, qualifiedName.removeHead(moduleLength), context) : null;
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static @Nullable PyFile findModule(QualifiedName moduleName, @Nullable PyFile contextFile, Project project) {
        if (contextFile != null && hasModulePath(contextFile, moduleName)) {
            return contextFile;
        }
        String shortName = moduleName.getLastComponent();
        if (shortName == null || DumbService.isDumb(project)) {
            return null;
        }
        List<PyFile> modules = new ArrayList<>();
        for (PyFile it : PyModuleNameIndex.findByShortName(shortName, project, GlobalSearchScope.projectScope(project))) {
            if (hasModulePath(it, moduleName)) {
                modules.add(it);
            }
        }
        if (modules.size() == 1) {
            return modules.get(0);
        }
        PyFile single = null;
        for (PyFile it : modules) {
            if (Objects.equals(it.getContainingDirectory(), contextFile != null ? contextFile.getContainingDirectory() : null)) {
                if (single != null) {
                    return null;
                }
                single = it;
            }
        }
        return single;
    }

    /**
     * Whether the file's path, {@code __init__} dropped, ends with {@code moduleName}, e.g. {@code src/x/n.py} with {@code n} or {@code x.n}.
     */
    private static boolean hasModulePath(PyFile pyFile, QualifiedName moduleName) {
        String name = pyFile.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        List<String> path = new ArrayList<>();
        if (!stem.equals(PyNames.INIT)) {
            path.add(stem);
        }
        VirtualFile virtualFile = pyFile.getVirtualFile();
        VirtualFile directory = virtualFile != null ? virtualFile.getParent() : null;
        while (directory != null && path.size() < moduleName.getComponentCount()) {
            path.add(directory.getName());
            directory = directory.getParent();
        }
        List<String> reversed = new ArrayList<>(moduleName.getComponents());
        Collections.reverse(reversed);
        return path.equals(reversed);
    }
}
