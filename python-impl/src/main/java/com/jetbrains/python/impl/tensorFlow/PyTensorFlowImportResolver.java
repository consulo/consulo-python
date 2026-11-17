// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.tensorFlow;

import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@ExtensionImpl
public class PyTensorFlowImportResolver implements PyImportResolver {

    @Override
    public @Nullable PsiElement resolveImportReference(QualifiedName name, PyQualifiedNameResolveContext context, boolean withRoots) {
        if (isTensorFlowMember(name)) {
            PyQualifiedNameResolveContext resolveContext = context.copyWithoutForeign();
            return resolveImportReference(name, resolveContext, PyTensorFlowKt.getTensorFlowPathConfig(resolveContext));
        }
        else {
            return null;
        }
    }

    private static boolean isTensorFlowMember(QualifiedName name) {
        return "tensorflow".equals(name.getFirstComponent()) && name.getComponentCount() > 1;
    }

    private static @Nullable PsiElement resolveImportReference(
        QualifiedName name,
        PyQualifiedNameResolveContext context,
        Map<String, String> pathConfig
    ) {
        String path = pathConfig.get(name.getComponents().get(1));
        if (path != null) {
            return PyTensorFlowKt.takeFirstResolvedInTensorFlow(path + "." + name.removeHead(2), context);
        }
        return PyTensorFlowKt.takeFirstResolvedInTensorFlow(pathConfig.get("*") + "." + name.removeHead(1), context);
    }
}
