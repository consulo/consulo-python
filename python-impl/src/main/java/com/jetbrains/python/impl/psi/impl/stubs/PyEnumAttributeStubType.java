// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyEnumAttributeStub;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import com.jetbrains.python.psi.impl.stubs.PyCustomStubType;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

@ExtensionImpl(id = "enumAttributeStub", order = "after typingNewTypeStub")
public class PyEnumAttributeStubType extends CustomTargetExpressionStubType<PyEnumAttributeStub> {
    @Override
    public @Nullable PyEnumAttributeStub createStub(PyTargetExpression psi) {
        if (!(ScopeUtil.getScopeOwner(psi) instanceof PyClass)) {
            return null;
        }

        if (!(PyPsiUtils.flattenParens(psi.findAssignedValue()) instanceof PyCallExpression callExpr)) {
            return null;
        }
        if (!(callExpr.getCallee() instanceof PyReferenceExpression callee)) {
            return null;
        }
        QualifiedName calleeFqn = ContainerUtil.getFirstItem(PyResolveUtil.resolveImportedElementQNameLocally(callee));

        PyExpression[] arguments = callExpr.getArguments();
        if (arguments.length != 1) {
            return null;
        }
        PyExpression argument = arguments[0];
        boolean isMember;
        if (Objects.equals(calleeFqn, PyKnownDecorator.ENUM_MEMBER.getQualifiedName())) {
            isMember = true;
        }
        else if (Objects.equals(calleeFqn, PyKnownDecorator.ENUM_NONMEMBER.getQualifiedName())) {
            isMember = false;
        }
        else {
            return null;
        }
        return new PyEnumAttributeStubImpl(PyLiteralKind.fromExpression(argument), isMember);
    }

    @Override
    public PyEnumAttributeStub deserializeStub(StubInputStream stream) throws IOException {
        PyLiteralKind literalKind = PyLiteralKind.deserialize(stream);
        boolean isMember = stream.readBoolean();
        return new PyEnumAttributeStubImpl(literalKind, isMember);
    }

    private static final class PyEnumAttributeStubImpl implements PyEnumAttributeStub {
        private final @Nullable PyLiteralKind literalKind;
        private final boolean isMember;

        private PyEnumAttributeStubImpl(@Nullable PyLiteralKind literalKind, boolean isMember) {
            this.literalKind = literalKind;
            this.isMember = isMember;
        }

        @Override
        public @Nullable PyLiteralKind getLiteralKind() {
            return literalKind;
        }

        @Override
        public boolean isMember() {
            return isMember;
        }

        @Override
        public Class<? extends PyCustomStubType<?, ?>> getTypeClass() {
            return PyEnumAttributeStubType.class;
        }

        @Override
        public void serialize(StubOutputStream stream) throws IOException {
            PyLiteralKind.serialize(stream, literalKind);
            stream.writeBoolean(isMember);
        }

        @Override
        public @Nullable QualifiedName getCalleeName() {
            return null;
        }

        @Override
        public String toString() {
            return "PyEnumAttributeStub(literalKind=" + literalKind + ", isMember=" + isMember + ")";
        }
    }
}
