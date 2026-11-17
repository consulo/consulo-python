// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.DataclassParameterArgumentMapping;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassParametersProvider;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassFieldStubImpl;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

@ExtensionImpl(id = "dataclass_transform")
public final class PyDataclassTransformParametersProvider implements PyDataclassParametersProvider {
    @Override
    public PyDataclassParameters.Type getType() {
        return PyDataclassTransformType.INSTANCE;
    }

    @Override
    public @Nullable Pair<PyDataclassStub, DataclassParameterArgumentMapping> buildDataclassStub(PyClass cls,
                                                                                                 @Nullable TypeEvalContext context) {
        return PyDataclassTransformResolverKt.buildDataclassTransformStubAndMapping(cls, context);
    }

    @Override
    public @Nullable PyDataclassFieldStub buildDataclassFieldStub(PyTargetExpression expression) {
        PyCallExpression call = PyDataclassFieldStubUtil.fieldInitializerCall(expression);
        if (call == null) {
            return null;
        }
        if (PyDataclassNames.DataclassTransform.FIELD_SPECIFIER_PARAMETERS.stream().noneMatch(it -> call.getKeywordArgument(it) != null)) {
            return null;
        }

        PyDataclassFieldStubUtil.FieldCallArgs args = PyDataclassFieldStubUtil.parseCommonFieldCallArgs(call, true);
        if (args == null) {
            return null;
        }
        PyDataclassResolver resolver = PyDataclassTransformType.INSTANCE.getResolver();
        return new PyDataclassFieldStubImpl(
            PyDataclassTransformType.INSTANCE.getName(),
            args.getQualifiedName(),
            args.getDefault() != null && !PyDataclassesKt.resolvesToOmittedDefault(resolver, args.getDefault()),
            args.getDefaultFactory() != null && !PyDataclassesKt.resolvesToOmittedDefault(resolver, args.getDefaultFactory()) ||
            args.getFactory() != null && !PyDataclassesKt.resolvesToOmittedDefault(resolver, args.getFactory()),
            args.getInitValue(),  // TODO How should we handle custom field specifiers where init=False by default
            args.getKwOnly(),
            args.getAlias()
        );
    }
}
