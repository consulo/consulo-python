// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.DataclassParameterArgumentMapping;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassParametersProvider;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Attrs;
import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassFieldStubImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyAttrsDataclassParametersProvider implements PyDataclassParametersProvider {
    @Override
    public PyDataclassParameters.Type getType() {
        return PyAttrsDataclassType.INSTANCE;
    }

    @Override
    public @Nullable Pair<PyDataclassStub, DataclassParameterArgumentMapping> buildDataclassStub(PyClass cls,
                                                                                                 @Nullable TypeEvalContext context) {
        return PyDataclassesKt.buildDecoratorDataclassStubAndMapping(cls, context);
    }

    @Override
    public @Nullable PyDataclassFieldStub buildDataclassFieldStub(PyTargetExpression expression) {
        PyCallExpression call = PyDataclassFieldStubUtil.fieldInitializerCall(expression);
        if (call == null) {
            return null;
        }
        if (PyDataclassFieldStubUtil.calleeQualifiedNames(call).stream().noneMatch(Attrs.FIELD_FUNCTIONS::contains)) {
            return null;
        }

        PyDataclassFieldStubUtil.FieldCallArgs args = PyDataclassFieldStubUtil.parseCommonFieldCallArgs(call, false);
        if (args == null) {
            return null;
        }
        PyDataclassResolver resolver = PyAttrsDataclassType.INSTANCE.getResolver();
        PyExpression factory = args.getFactory();
        boolean hasFactory = factory != null && !factory.getText().equals(PyNames.NONE);

        PyExpression defaultValue = args.getDefault();
        if (defaultValue != null && !PyDataclassesKt.resolvesToOmittedDefault(resolver, defaultValue)) {
            PyReferenceExpression callee = defaultValue instanceof PyCallExpression defaultCall &&
                                           defaultCall.getCallee() instanceof PyReferenceExpression defaultCallee ? defaultCallee : null;
            boolean hasFactoryInDefault =
                callee != null &&
                PyResolveUtil.resolveImportedElementQNameLocally(callee).stream().anyMatch(it -> Attrs.ATTRS_FACTORY.contains(it.toString()));

            return new PyDataclassFieldStubImpl(
                PyAttrsDataclassType.INSTANCE.getName(),
                args.getQualifiedName(),
                !hasFactoryInDefault,
                hasFactory || hasFactoryInDefault,
                args.getInitValue(),
                args.getKwOnly(),
                args.getAlias()
            );
        }
        return new PyDataclassFieldStubImpl(
            PyAttrsDataclassType.INSTANCE.getName(),
            args.getQualifiedName(),
            false,
            hasFactory,
            args.getInitValue(),
            args.getKwOnly(),
            args.getAlias()
        );
    }
}
