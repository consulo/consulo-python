// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyTypingNewTypeStub;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public class PyTypingNewTypeStubImpl implements PyTypingNewTypeStub {
    private static final String NEW_TYPE_NAME = "NewType";

    private final String qualifiedName;
    private final String baseClassName;
    private final @Nullable String unresolvedCalleeName;

    private PyTypingNewTypeStubImpl(String qualifiedName, String baseClassName, @Nullable String unresolvedCalleeName) {
        this.qualifiedName = qualifiedName;
        this.baseClassName = baseClassName;
        this.unresolvedCalleeName = unresolvedCalleeName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
        stream.writeName(qualifiedName);
        stream.writeName(baseClassName);
        stream.writeName(unresolvedCalleeName);
    }

    @Override
    public QualifiedName getCalleeName() {
        return QualifiedName.fromComponents(qualifiedName);
    }

    @Override
    public Class<PyTypingNewTypeStubType> getTypeClass() {
        return PyTypingNewTypeStubType.class;
    }

    @Override
    public String getName() {
        return qualifiedName;
    }

    @Override
    public String getClassType() {
        return baseClassName;
    }

    @Override
    public @Nullable String getUnresolvedCalleeName() {
        return unresolvedCalleeName;
    }

    @Override
    public String toString() {
        return "PyTypingNewTypeStub(qualifiedName='" + qualifiedName + "', baseClass='" + baseClassName +
               "', unresolvedCallee=" + unresolvedCalleeName + ")";
    }

    public static @Nullable PyTypingNewTypeStub create(PyTargetExpression expression) {
        PyExpression assignedValue = expression.findAssignedValue();
        return assignedValue instanceof PyCallExpression callExpression ? create(callExpression) : null;
    }

    public static @Nullable PyTypingNewTypeStub create(PyCallExpression expression) {
        if (!(expression.getCallee() instanceof PyReferenceExpression callee)) {
            return null;
        }
        String unresolvedCalleeName;
        if (isTypingNewType(callee)) {
            unresolvedCalleeName = null;
        }
        // The callee may be a re-export of `typing.NewType` from another module, which stubs cannot resolve
        else if (NEW_TYPE_NAME.equals(callee.getReferencedName())) {
            QualifiedName calleeQName = callee.asQualifiedName();
            if (calleeQName == null) {
                return null;
            }
            unresolvedCalleeName = calleeQName.toString();
        }
        else {
            return null;
        }
        String newTypeName = PyResolveUtil.resolveStrArgument(expression, 0, "name");
        if (newTypeName == null) {
            return null;
        }
        PyExpression secondArgument = PyPsiUtils.flattenParens(expression.getArgument(1, "tp", PyExpression.class));
        if (secondArgument == null) {
            return null;
        }
        return new PyTypingNewTypeStubImpl(newTypeName, secondArgument.getText(), unresolvedCalleeName);
    }

    public static @Nullable PyTypingNewTypeStub deserialize(@Nullable StubInputStream stream) throws IOException {
        if (stream != null) {
            String typeName = stream.readNameString();
            String classType = stream.readNameString();
            String unresolvedCalleeName = stream.readNameString();
            if (typeName != null && classType != null) {
                return new PyTypingNewTypeStubImpl(typeName, classType, unresolvedCalleeName);
            }
        }
        return null;
    }

    private static boolean isTypingNewType(PyReferenceExpression callee) {
        return PyResolveUtil.resolveImportedElementQNameLocally(callee).stream().anyMatch(
            it -> it.toString().equals(PyTypingTypeProvider.NEW_TYPE) || it.toString().equals(PyTypingTypeProvider.NEW_TYPE_EXT)
        );
    }
}
