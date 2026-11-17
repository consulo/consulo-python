// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;


public class PyImportedModuleType implements PyType {
    private final PyImportedModule myImportedModule;

    public PyImportedModuleType(PyImportedModule importedModule) {
        myImportedModule = importedModule;
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {

        List<PsiElement> importedModuleCandidates = ResolveResultList.getElements(myImportedModule.multiResolve());
        SmartList<RatedResolveResult> primaryResults = new SmartList<>();
        for (PsiElement moduleLocation : importedModuleCandidates) {
            PsiFileSystemItem resolvedModuleOrPackage = PyUtil.as(moduleLocation, PsiFileSystemItem.class);
            if (resolvedModuleOrPackage != null) {
                List<? extends RatedResolveResult> results =
                    PyModuleType.resolveMemberInPackageOrModule(myImportedModule, resolvedModuleOrPackage, name, location, resolveContext);

                if (results != null) {
                    primaryResults.addAll(results);
                }
            }
        }

        if (!primaryResults.isEmpty()) {
            return PyUtil.filterTopPriorityResults(primaryResults);
        }

        for (PsiElement moduleLocation : importedModuleCandidates) {
            PsiFileSystemItem resolvedModuleOrPackage = PyUtil.as(moduleLocation, PsiFileSystemItem.class);
            if (resolvedModuleOrPackage == null) {
                continue;
            }
            //if it's a  synthetic import element
            if (myImportedModule.getImportElement() == null) {
                PsiFile resolvingFromFile = location != null ? location.getContainingFile() : null;
                List<RatedResolveResult> fallbackResults =
                    ResolveImportUtil.resolveChildren(resolvedModuleOrPackage, name, resolvingFromFile, false, true,
                                                      false, false);

                return ResolveResultList.asImportedResults(fallbackResults, null);
            }
        }
        return null;
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        List<LookupElement> result = new ArrayList<>();
        for (PsiElement resolveResult : ResolveResultList.getElements(myImportedModule.multiResolve())) {
            if (resolveResult instanceof PyFile) {
                PyModuleType moduleType = new PyModuleType((PyFile)resolveResult);
                TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());

                result.addAll(moduleType.getCompletionVariantsAsLookupElements(location, context, false, false, typeEvalContext));
            }
            else if (resolveResult instanceof PsiDirectory dir) {
                if (PyUtil.isPackage(dir, location)) {
                    if (ResolveImportUtil.getPointInImport(location) != PointInImport.NONE) {
                        result.addAll(PyModuleType.getSubModuleVariants(dir, location, null));
                    }
                    else {
                        result.addAll(PyModuleType.collectImportedSubmodulesAsLookupElements(dir, location, context.get(CTX_NAMES)));
                    }
                }
            }
        }
        return ArrayUtil.toObjectArray(result);
    }

    @Override
    public String getName() {
        return myImportedModule.getImportedPrefix().toString();
    }

    @Override
    public boolean isBuiltin() {
        return false;  // no module can be imported from builtins
    }

    @Override
    public void assertValid(String message) {
    }

    public PyImportedModule getImportedModule() {
        return myImportedModule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyImportedModuleType type = (PyImportedModuleType)o;
        return Objects.equals(myImportedModule, type.myImportedModule);
    }

    @Override
    public int hashCode() {

        return Objects.hash(myImportedModule);
    }
}
