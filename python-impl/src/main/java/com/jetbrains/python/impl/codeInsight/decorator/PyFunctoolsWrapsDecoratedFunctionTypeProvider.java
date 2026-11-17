// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.decorator;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyFunctoolsWrapsDecoratorStub;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Infer type for reference of a function decorated with 'functools.wraps'.
 * Has to be used before {@code com.jetbrains.python.codeInsight.decorator.PyDecoratedFunctionTypeProvider}
 */
@ExtensionImpl(id = "pyFunctoolsWrapsDecoratedFunctionTypeProvider", order = "after pythonDocstringTypeProvider, before pyDecoratedFunctionTypeProvider")
public class PyFunctoolsWrapsDecoratedFunctionTypeProvider extends PyTypeProviderBase {
    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        if (!(referenceTarget instanceof PyFunction function)) {
            return null;
        }
        PyFunction wrappedFunction = ContainerUtil.findInstance(resolveWrapped(function, context), PyFunction.class);
        if (wrappedFunction == null) {
            return null;
        }
        return Ref.create(context.getType(wrappedFunction));
    }

    @Override
    public @Nullable PyType getCallableType(PyCallable callable, TypeEvalContext context) {
        Ref<PyType> ref = getReferenceType(callable, context, null);
        return ref != null ? ref.get() : null; // TODO consulo: Ref.deref missing
    }

    private static List<PsiElement> resolveWrapped(PyFunction function, TypeEvalContext context) {
        PyDecoratorList decoratorList = function.getDecoratorList();
        PyDecorator decorator = null;
        if (decoratorList != null) {
            for (PyDecorator it : decoratorList.getDecorators()) {
                QualifiedName qName = it.getQualifiedName();
                if (qName != null && PyKnownDecoratorUtil.asKnownDecorators(qName).contains(PyKnownDecorator.FUNCTOOLS_WRAPS)) {
                    decorator = it;
                    break;
                }
            }
        }
        if (decorator == null) {
            return Collections.emptyList();
        }
        PyDecorator finalDecorator = decorator;
        return StubAwareComputation.on(decorator)
            .withCustomStub(it -> it.getCustomStub(PyFunctoolsWrapsDecoratorStub.class))
            .overStub(it -> {
                if (it == null) {
                    return Collections.<PsiElement>emptyList();
                }
                ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(finalDecorator);
                QualifiedName wrappedQName = QualifiedName.fromDottedString(it.getWrapped());
                return PyResolveUtil.resolveQualifiedNameInScope(wrappedQName, scopeOwner, context);
            })
            .overAst(it -> {
                PyArgumentList argumentList = it.getArgumentList();
                PyExpression wrappedExpr = argumentList != null
                    ? argumentList.getValueExpressionForParam(PyKnownDecoratorUtil.FunctoolsWrapsParameters.WRAPPED)
                    : null;
                if (wrappedExpr == null) {
                    return Collections.<PsiElement>emptyList();
                }
                return PyUtil.multiResolveTopPriority(wrappedExpr, PyResolveContext.defaultContext(context));
            })
            .withStubBuilder(PyFunctoolsWrapsDecoratorStub::create)
            .compute(context);
    }
}
