// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.codeinsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public class PyTypeNameResolver {
    private static final String BUILTINS_MODULE_FOR_PYTHON2 = "__builtin__";
    private static final String BUILTINS_MODULE_FOR_PYTHON3 = "builtins";

    private final Project myProject;

    public PyTypeNameResolver(Project project) {
        myProject = project;
    }

    public Project getProject() {
        return myProject;
    }

    private static @Nullable PyClass getClass(PyFile file, String name) {
        PyModuleType moduleType = new PyModuleType(file);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(file.getProject()));
        List<? extends RatedResolveResult> results = moduleType.resolveMember(name, null, AccessDirection.READ, resolveContext);
        if (results != null) {
            for (RatedResolveResult result : results) {
                if (result.getElement() instanceof PyClass pyClass) {
                    return pyClass;
                }
            }
        }
        return null;
    }

    private @Nullable PyClass getClassFromModule(String clsName, List<String> moduleComponents) {
        for (PsiElement element : getElementsFromModule(moduleComponents, myProject)) {
            if (element instanceof PyFile pyFile) {
                PyClass cls = getClass(pyFile, clsName);
                if (cls != null) {
                    return cls;
                }
            }
        }
        return null;
    }

    public @Nullable PyClass resolve(String qualifiedName) {
        List<String> components = Arrays.asList(qualifiedName.split("\\.", -1));
        List<String> moduleComponents = components.subList(0, components.size() - 1);
        String clsName = components.get(components.size() - 1);

        if (moduleComponents.isEmpty()) {
            PyClass cls = getClassFromModule(clsName, List.of(BUILTINS_MODULE_FOR_PYTHON2));
            if (cls != null) {
                return cls;
            }
            return getClassFromModule(clsName, List.of(BUILTINS_MODULE_FOR_PYTHON3));
        }

        return getClassFromModule(clsName, moduleComponents);
    }

    public static int getClassesNumberInModuleRootWithName(PyClass cls, QualifiedName clsCanonicalImportPath, Project project) {
        String clsName = cls.getName();
        if (clsName == null) {
            return 0;
        }
        GlobalSearchScope scope = PySearchUtilBase.defaultSuggestionScope(cls);
        Set<QualifiedName> groups = new HashSet<>();
        for (PyClass it : PyClassNameIndex.find(clsName, project, scope)) {
            QualifiedName canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(it, null);
            if (canonicalImportPath != null &&
                Objects.equals(canonicalImportPath.getFirstComponent(), clsCanonicalImportPath.getFirstComponent())) {
                groups.add(canonicalImportPath);
            }
        }
        return groups.size();
    }

    public static List<PsiElement> getElementsFromModule(List<String> moduleComponents, Project project) {
        QualifiedName qualifiedName = QualifiedName.fromComponents(moduleComponents);
        List<PsiElement> elements = new ArrayList<>();

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            PyQualifiedNameResolveContext context = PyResolveImportUtil.fromModule(module);
            List<PsiElement> psiElements = PyResolveImportUtil.resolveQualifiedName(qualifiedName, context);

            for (PsiElement element : psiElements) {
                if (element instanceof PsiDirectory directory) {
                    PsiFile initPy = directory.findFile(PyNames.INIT_DOT_PY);
                    if (initPy instanceof PyFile) {
                        elements.add(initPy);
                    }
                    PsiFile initPyi = directory.findFile(PyNames.INIT_DOT_PYI);
                    if (initPyi instanceof PyFile) {
                        elements.add(initPyi);
                    }
                    elements.add(element);
                }
                else if (element instanceof PyFile) {
                    elements.add(element);
                }
            }

            if (!elements.isEmpty()) {
                return elements;
            }
        }

        return elements;
    }
}
