// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.find.FindUsagesHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public interface PyPsiFindUsagesHandlerFactory {
    default boolean canFindUsages(PsiElement element) {
        return element instanceof PyClass ||
            (element instanceof PyFile && PyUtil.isPackage((PyFile) element)) ||
            element instanceof PyImportedModule ||
            element instanceof PyFunction ||
            element instanceof PyTargetExpression;
    }

    default @Nullable FindUsagesHandler createFindUsagesHandler(PsiElement element, boolean forHighlightUsages) {
        if (element instanceof PyImportedModule) {
            final PsiElement resolved = ((PyImportedModule) element).resolve();
            if (resolved != null) {
                element = resolved;
            }
        }
        if (element instanceof PsiFileSystemItem) {
            return createModuleFindUsagesHandler((PsiFileSystemItem) element);
        }
        if (element instanceof PyFunction) {
            if (!forHighlightUsages) {
                TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), null);
                final Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction) element, true, context).findAll();
                if (!superMethods.isEmpty()) {
                    final PsiElement next = superMethods.iterator().next();
                    // TODO should do this for Jython functions overriding Java methods too
                    if (next instanceof PyFunction && !isInObject((PyFunction) next)) {
                        if (PythonUiService.getInstance().showYesDialog(element.getProject(),
                            PyLocalize.pythonFindUsagesBaseMethodQuestion(((PyFunction) element).getName(),
                                ((PyFunction) next).getContainingClass().getName()).get(),
                            PyLocalize.pythonFindUsages().get())) {
                            List<PsiElement> allMethods = new ArrayList<>();
                            allMethods.add(element);
                            allMethods.addAll(superMethods);
                            return new PyFunctionFindUsagesHandler(element, allMethods);
                        }
                        else {
                            return new PyFunctionFindUsagesHandler(element);
                        }
                    }
                }
            }
            return new PyFunctionFindUsagesHandler(element);
        }
        if (element instanceof PyClass) {
            return new PyClassFindUsagesHandler((PyClass) element);
        }
        if (element instanceof PyTargetExpression) {
            return new PyTargetExpressionFindUsagesHandler(((PyTargetExpression) element));
        }
        return null;
    }

    default PyModuleFindUsagesHandler createModuleFindUsagesHandler(PsiFileSystemItem element) {
        return new PyModuleFindUsagesHandler(element);
    }

    static boolean isInObject(PyFunction fun) {
        final PyClass containingClass = fun.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        return PyUtil.isObjectClass(containingClass);
    }
}
