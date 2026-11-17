// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageTarget;
import consulo.usage.UsageType;
import consulo.usage.UsageTypeProviderEx;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyUsageTypeProvider implements UsageTypeProviderEx {
    private static final UsageType IN_IMPORT = new UsageType(PyLocalize.pythonFindUsagesUsageInImportStatement());
    private static final UsageType UNTYPED = new UsageType(PyLocalize.pythonFindUsagesUntypedProbableUsage());
    private static final UsageType USAGE_IN_ISINSTANCE = new UsageType(PyLocalize.pythonFindUsagesUsageInIsinstance());
    private static final UsageType USAGE_IN_SUPERCLASS = new UsageType(PyLocalize.pythonFindUsagesUsageInSuperclassList());
    private static final UsageType USAGE_IN_TYPE_HINT = new UsageType(PyLocalize.pythonFindUsagesUsageInTypeHint());

    @Override
    public @Nullable UsageType getUsageType(PsiElement element) {
        return getUsageType(element, UsageTarget.EMPTY_ARRAY);
    }

    @Override
    public @Nullable UsageType getUsageType(PsiElement element, UsageTarget[] targets) {
        TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
        if (element instanceof PyElement) {
            if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
                return IN_IMPORT;
            }
            if (element instanceof PyQualifiedExpression) {
                PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
                if (qualifier != null) {
                    PyType type = context.getType(qualifier);
                    if (type == null || type instanceof PyStructuralType) {
                        return UNTYPED;
                    }
                }
            }
            if (element instanceof PyReferenceExpression) {
                PyCallExpression call = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
                if (call != null && call.isCalleeText(PyNames.ISINSTANCE)) {
                    PyExpression[] args = call.getArguments();
                    if (args.length == 2) {
                        PyExpression typeExpression = args[1];
                        if (element == typeExpression) {
                            return USAGE_IN_ISINSTANCE;
                        }
                        typeExpression = PyPsiUtils.flattenParens(typeExpression);
                        if (typeExpression instanceof PySequenceExpression && element.getParent() == typeExpression) {
                            return USAGE_IN_ISINSTANCE;
                        }
                    }
                }
                if (PyTypingTypeProvider.isInsideTypeHint(element, context)) {
                    return USAGE_IN_TYPE_HINT;
                }
                PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
                if (pyClass != null && PsiTreeUtil.isAncestor(pyClass.getSuperClassExpressionList(), element, true)) {
                    return USAGE_IN_SUPERCLASS;
                }
            }
        }
        return null;
    }
}
