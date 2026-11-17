// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.extensions.PsiFileSystemItemExtKt;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyOverridingModuleMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

@ExtensionImpl
public class PyStdlibOverridingModuleMembersProvider extends PyOverridingModuleMembersProvider {
    @Override
    protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName, TypeEvalContext context) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable PsiElement resolveMember(PyFile module, String name, PyResolveContext resolveContext) {
        if (module.getLanguageLevel().isPython2() &&
            name.equals("md5") &&
            module.getName().equals("hashlib.py") &&
            QualifiedName.fromComponents("hashlib").equals(PsiFileSystemItemExtKt.getQName(module))) {
            // When module member is not found in it,
            // `__all__` and nearby module with the specified name are suggested as a fallback.

            // In case of `hashlib` in Python 2 stdlib, returned `md5` module is not used in `hashlib`
            // so the behaviour has to be overridden.

            // Please note that it's important to check against `hashlib.py`
            // because `hashlib.pyi` stub is defined correctly.

            return module.findExportedName(PyNames.ALL);
        }

        return null;
    }
}
