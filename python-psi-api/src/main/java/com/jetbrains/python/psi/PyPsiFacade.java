// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeRendererFeature;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@ServiceAPI(ComponentScope.PROJECT)
public abstract class PyPsiFacade {
    public static PyPsiFacade getInstance(Project project) {
        return project.getInstance(PyPsiFacade.class);
    }

    public abstract List<PsiElement> resolveQualifiedName(QualifiedName name,
            PyQualifiedNameResolveContext context);

    public abstract PyQualifiedNameResolveContext createResolveContextFromFoothold(PsiElement foothold);

    public abstract PyClassType createClassType(PyClass pyClass, boolean isDefinition);

    public abstract @Nullable PyType createUnionType(Collection<PyType> members);

    public abstract @Nullable PyType createTupleType(List<PyType> members, PsiElement anchor);

    public abstract @Nullable PyType parseTypeAnnotation(String annotation, PsiElement anchor);

    /**
     * Render a {@link PyType} into a human-readable string, e.g. for presentation in the UI.
     *
     * @param type     the type to render, or {@code null} to render the "unknown" type
     * @param context  context used to infer extra types
     * @param features optional features tweaking the rendering, e.g.
     *                 {@link PyTypeRendererFeature#TYPE_VAR_BOUNDS} to render {@code T ≤: str} instead of just {@code T}
     * @return a plain-text representation of the type
     */
    public abstract String renderType(@Nullable PyType type,
            TypeEvalContext context,
            Set<PyTypeRendererFeature> features);

    /**
     * Retrieve a top-level class by its qualified name. The name provided is supposed to be <em>fully qualified absolute name</em>
     * of the class, neither relative to the containing file of the anchor element, nor dependent on its imports.
     * <p>
     * The only exception to the rule above are built-in classes as it's too cumbersome to explicitly specify "__builtin__" or "builtins"
     * prefix for them each time, and, overall, it's rather intuitive that these classes can be found solely by their short names.
     * <p>
     * The anchor element is needed only to detect the corresponding module and its SDK.
     *
     * @param qName  qualified name of the required class
     * @param anchor arbitrary element located in the same module/SDK as the required class
     */
    public @Nullable PyClass createClassByQName(QualifiedName qName, PsiElement anchor) {
        return createClassByQName(qName.toString(), anchor);
    }

    public abstract @Nullable PyClass createClassByQName(String qName, PsiElement anchor);

    public abstract @Nullable String findShortestImportableName(VirtualFile targetFile, PsiElement anchor);

    /**
     * @deprecated Use {@link LanguageLevel#forElement(PsiElement)}
     */
    @Deprecated(forRemoval = true)
    public abstract LanguageLevel getLanguageLevel(PsiElement element);
}
