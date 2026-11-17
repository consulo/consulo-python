// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.stubs.PyTypingNewTypeStubImpl;
import com.jetbrains.python.impl.psi.stubs.PyTypingNewTypeStub;
import com.jetbrains.python.impl.psi.types.PyTypingNewType;
import com.jetbrains.python.impl.psi.types.PyTypingNewTypeFactoryType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

@ExtensionImpl(id = "pyTypingNewTypeTypeProvider", order = "after pyTypedDictTypeProvider")
public class PyTypingNewTypeTypeProvider extends PyTypeProviderBase {
    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        PyTypingNewType newType = Helper.getNewTypeForResolvedElement(referenceTarget, context);
        return newType != null ? Ref.create(new PyTypingNewTypeFactoryType(newType)) : null;
    }

    public static final class Helper {
        private Helper() {
        }

        public static @Nullable PyTypingNewType getNewTypeForResolvedElement(PsiElement element, TypeEvalContext context) {
            if (element instanceof PyTargetExpression target) {
                return StubAwareComputation.on(target)
                    .withCustomStub(it -> it.getCustomStub(PyTypingNewTypeStub.class))
                    .overStub(it -> getNewTypeFromStub(target, it, context))
                    .withStubBuilder(it -> PyTypingNewTypeStubImpl.create(it))
                    .compute(context);
            }
            return null;
        }

        private static @Nullable PyTypingNewType getNewTypeFromStub(PyTargetExpression target,
                                                                    @Nullable PyTypingNewTypeStub stub,
                                                                    TypeEvalContext context) {
            if (stub == null) {
                return null;
            }
            String unresolvedCalleeName = stub.getUnresolvedCalleeName();
            if (unresolvedCalleeName != null && !resolvesToNewType(unresolvedCalleeName, target, context)) {
                return null;
            }
            // TODO consulo: Ref.deref missing
            Ref<PyType> typeRef = PyTypingTypeProvider.getStringBasedType(stub.getClassType(), target, context);
            PyType type = typeRef != null ? typeRef.get() : null;
            return type instanceof PyClassType classType ? new PyTypingNewType(classType.toClass(), stub.getName(), target) : null;
        }

        private static boolean resolvesToNewType(String calleeName, PyTargetExpression target, TypeEvalContext context) {
            PyExpression callee = PyUtil.createExpressionFromFragment(calleeName, target);
            if (callee == null) {
                return false;
            }
            Collection<String> qualifiedNames = PyTypingTypeProvider.resolveToQualifiedNames(callee, context);
            return qualifiedNames.contains(PyTypingTypeProvider.NEW_TYPE) || qualifiedNames.contains(PyTypingTypeProvider.NEW_TYPE_EXT);
        }
    }
}
