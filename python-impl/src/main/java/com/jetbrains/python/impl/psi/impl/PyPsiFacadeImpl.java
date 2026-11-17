// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ObjectUtil;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeRendererFeature;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ServiceImpl
@Singleton
public final class PyPsiFacadeImpl extends PyPsiFacade {
    private final Project myProject;

    @Inject
    public PyPsiFacadeImpl(Project project) {
        myProject = project;
    }

    @Override
    public List<PsiElement> resolveQualifiedName(QualifiedName name, PyQualifiedNameResolveContext context) {
        return PyResolveImportUtil.resolveQualifiedName(name, context);
    }

    @Override
    public PyQualifiedNameResolveContext createResolveContextFromFoothold(PsiElement foothold) {
        return PyResolveImportUtil.fromFoothold(foothold);
    }

    @Override
    public PyClassType createClassType(PyClass pyClass, boolean isDefinition) {
        return new PyClassTypeImpl(pyClass, isDefinition);
    }

    @Override
    public @Nullable PyType createUnionType(Collection<PyType> members) {
        return PyUnionType.unionOrUnknown(members);
    }

    @Override
    public @Nullable PyType createTupleType(List<PyType> members, PsiElement anchor) {
        return PyTupleType.create(anchor, members);
    }

    @Override
    public @Nullable PyType parseTypeAnnotation(String annotation, PsiElement anchor) {
        return PyLegacyDocstringTypeParser.getTypeByName(anchor, annotation);
    }

    @Override
    public String renderType(@Nullable PyType type, TypeEvalContext context, Set<PyTypeRendererFeature> features) {
        return PythonDocumentationProvider.getTypeName(type, context, features.toArray(new PyTypeRendererFeature[0]));
    }

    public static List<@Nullable PsiElement> resolveQName(QualifiedName qualifiedName, PsiElement anchor) {
        // Only built-in classes can be found by their unqualified names.
        if (qualifiedName.getComponentCount() == 1) {
            PyClass aClass = PyBuiltinCache.getInstance(anchor).getClass(qualifiedName.toString());
            return aClass != null ? List.of(aClass) : Collections.emptyList();
        }

        Module module = ModuleUtilCore.findModuleForPsiElement(ObjectUtil.notNull(anchor.getContainingFile(), anchor));
        if (module == null) {
            return Collections.emptyList();
        }
        // Don't use PyResolveImportUtil.fromFoothold here as setting foothold file is going to affect resolve results
        // particularly if the anchor element happens to be in the same file as the target class.
        PyQualifiedNameResolveContext resolveContext = PyResolveImportUtil.fromModule(module).copyWithMembers();
        return PyResolveImportUtil.resolveQualifiedName(qualifiedName, resolveContext);
    }

    @Override
    public @Nullable PyClass createClassByQName(String qName, PsiElement anchor) {
        return resolveQName(QualifiedName.fromDottedString(qName), anchor).stream()
            .filter(PyClass.class::isInstance)
            .map(PyClass.class::cast)
            .findFirst()
            .orElse(null);
    }

    @Override
    public @Nullable String findShortestImportableName(VirtualFile targetFile, PsiElement anchor) {
        return QualifiedNameFinder.findShortestImportableName(anchor, targetFile);
    }

    @Override
    public LanguageLevel getLanguageLevel(PsiElement element) {
        return LanguageLevel.forElement(element);
    }
}
