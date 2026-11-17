// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesHandlerFactory;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.MessageBoxBuilder;
import consulo.ui.MessageButtonRole;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ExtensionImpl(id = "Python", order = "before default")
public final class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory implements PyPsiFindUsagesHandlerFactory {

    @Override
    public boolean canFindUsages(PsiElement element) {
        return PyPsiFindUsagesHandlerFactory.super.canFindUsages(element);
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(PsiElement element, boolean forHighlightUsages) {
        // Consulo: PyFindUsagesHandler is a FindUsagesHandler (no FindUsagesHandlerBase / FindUsagesHandlerUi split),
        // so JB's proxy() wrapping is not needed; PyModuleFindUsagesHandler provides the dialog itself
        return PyPsiFindUsagesHandlerFactory.super.createFindUsagesHandler(element, forHighlightUsages);
    }

    // Consulo: asynchronous variant that asks about the base method without a modal dialog
    @Override
    @RequiredUIAccess
    public CompletableFuture<@Nullable FindUsagesHandler> createFindUsagesHandlerAsync(PsiElement element, boolean forHighlightUsages) {
        if (forHighlightUsages || !(element instanceof PyFunction function)) {
            return CompletableFuture.completedFuture(createFindUsagesHandler(element, forHighlightUsages));
        }
        TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), null);
        Collection<PsiElement> superMethods = PySuperMethodsSearch.search(function, true, context).findAll();
        if (superMethods.isEmpty()
            || !(superMethods.iterator().next() instanceof PyFunction next)
            || PyPsiFindUsagesHandlerFactory.isInObject(next)) {
            return CompletableFuture.completedFuture(new PyFunctionFindUsagesHandler(element));
        }

        List<PsiElement> allMethods = new ArrayList<>();
        allMethods.add(element);
        allMethods.addAll(superMethods);
        return MessageBoxBuilder.<@Nullable FindUsagesHandler>create()
            .asQuestion()
            .title(PyLocalize.pythonFindUsages())
            .text(PyLocalize.pythonFindUsagesBaseMethodQuestion(function.getName(), next.getContainingClass().getName()))
            .button(MessageButtonRole.YES, new PyFunctionFindUsagesHandler(element, allMethods))
            .asDefaultButton()
            .button(MessageButtonRole.NO, new PyFunctionFindUsagesHandler(element))
            .asExitButton()
            .showAsync(element.getProject());
    }
}
