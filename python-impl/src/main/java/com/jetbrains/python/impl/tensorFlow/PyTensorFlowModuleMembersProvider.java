// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.tensorFlow;

import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtensionImpl
public class PyTensorFlowModuleMembersProvider extends PyModuleMembersProvider {

    @Override
    public Collection<PyCustomMember> getMembers(PyFile module, PointInImport point, TypeEvalContext context) {
        if (isTensorFlow(module)) {
            PyQualifiedNameResolveContext resolveContext = PyResolveImportUtil.fromFoothold(module).copyWithoutForeign();
            return getMembers(resolveContext, PyTensorFlowKt.getTensorFlowPathConfig(resolveContext));
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public @Nullable PsiElement resolveMember(PyFile module, String name, PyResolveContext resolveContext) {
        if (isTensorFlow(module)) {
            PyQualifiedNameResolveContext context = PyResolveImportUtil.fromFoothold(module).copyWithoutForeign();
            return resolveMember(name, context, PyTensorFlowKt.getTensorFlowPathConfig(context));
        }
        else {
            return null;
        }
    }

    @Override
    protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName, TypeEvalContext context) {
        return Collections.emptyList();
    }

    private static boolean isTensorFlow(PyFile module) {
        QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(module);
        return qName != null && qName.matches("tensorflow");
    }

    private static Collection<PyCustomMember> getMembers(
        PyQualifiedNameResolveContext context,
        Map<String, String> pathConfig
    ) {
        List<PyCustomMember> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : pathConfig.entrySet()) {
            if (entry.getKey().equals("*")) {
                continue;
            }
            PyCustomMember member =
                resolvedModuleToCustomMember(PyTensorFlowKt.takeFirstResolvedInTensorFlow(entry.getValue(), context), entry.getKey());
            if (member != null) {
                result.add(member);
            }
        }

        if (PyTensorFlowKt.takeFirstResolvedInTensorFlow(pathConfig.get("*"), context) instanceof PsiDirectory dir) {
            for (PsiDirectory subdir : dir.getSubdirectories()) {
                PyCustomMember member = resolvedModuleToCustomMember(subdir, subdir.getName());
                if (member != null) {
                    result.add(member);
                }
            }
        }

        return result;
    }

    private static @Nullable PsiElement resolveMember(
        String name,
        PyQualifiedNameResolveContext context,
        Map<String, String> pathConfig
    ) {
        if (pathConfig.containsKey(name)) {
            return PyTensorFlowKt.takeFirstResolvedInTensorFlow(pathConfig.get(name), context);
        }

        if (PyTensorFlowKt.takeFirstResolvedInTensorFlow(pathConfig.get("*"), context) instanceof PsiDirectory dir) {
            for (PsiDirectory subdir : dir.getSubdirectories()) {
                if (subdir.getName().equals(name)) {
                    return subdir;
                }
            }
            return null;
        }

        return null;
    }

    private static @Nullable PyCustomMember resolvedModuleToCustomMember(@Nullable PsiElement module, String name) {
        PsiElement init = PyUtil.turnDirIntoInit(module);
        return init != null ? new PyCustomMember(name, init, null) : null;
    }
}
