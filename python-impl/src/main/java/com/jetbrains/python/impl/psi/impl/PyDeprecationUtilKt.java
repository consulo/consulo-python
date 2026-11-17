// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.ast.PyAstDecoratable;
import com.jetbrains.python.ast.PyAstDecorator;
import com.jetbrains.python.ast.PyAstDecoratorList;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

/**
 * Java facade of JB {@code PyDeprecationUtil.kt}.
 */
public final class PyDeprecationUtilKt {
    private static final String[] deprecationDecorators = {"typing_extensions.deprecated", "warnings.deprecated"};

    private PyDeprecationUtilKt() {
    }

    public static String[] getDeprecationDecorators() {
        return deprecationDecorators;
    }

    public static @Nullable String extractDeprecationMessageFromDecorator(PyAstDecoratable element) {
        PyAstDecoratorList decoratorList = element.getDecoratorList();
        if (decoratorList == null) {
            return null;
        }
        PyAstDecorator deprecatedDecorator = null;
        for (PyAstDecorator it : decoratorList.getDecorators()) {
            if ("deprecated".equals(it.getName())) {
                deprecatedDecorator = it;
                break;
            }
        }
        if (deprecatedDecorator == null) {
            return null;
        }
        if (deprecatedDecorator.getArguments().length == 0) {
            return null;
        }
        if (!(deprecatedDecorator.getCallee() instanceof PyReferenceExpression decoratorCall)) {
            return null;
        }
        QualifiedName qualifiedName = decoratorCall.asQualifiedName();
        if (!ArrayUtil.contains(qualifiedName != null ? qualifiedName.toString() : null, deprecationDecorators)) {
            boolean importedFromDeprecationModule = false;
            for (PsiElement resolved : PyResolveUtil.resolveLocally(decoratorCall)) {
                if (resolved.getParent() instanceof PyFromImportStatement fromImportStatement) {
                    for (String name : fromImportStatement.getFullyQualifiedObjectNames()) {
                        if (ArrayUtil.contains(name, deprecationDecorators)) {
                            importedFromDeprecationModule = true;
                            break;
                        }
                    }
                }
                if (importedFromDeprecationModule) {
                    break;
                }
            }
            if (!importedFromDeprecationModule) {
                return null;
            }
        }

        return deprecatedDecorator.getArguments()[0] instanceof PyAstStringLiteralExpression stringLiteral ? stringLiteral.getStringValue() : null;
    }
}
