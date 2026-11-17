// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.*;
import com.google.common.collect.RangeSet;
import consulo.language.ast.ASTNode;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.Version;
import consulo.language.psi.PsiElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.index.io.data.DataInputOutputUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstParameter;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.impl.psi.stubs.PyVariableNameIndex;

import java.io.IOException;
import java.util.List;


public class PyTargetExpressionElementType extends PyStubElementType<PyTargetExpressionStub, PyTargetExpression>
    implements
    PyCustomizableStubElementType<PyTargetExpression, CustomTargetExpressionStub, CustomTargetExpressionStubType<? extends CustomTargetExpressionStub>> {
    private static final byte QUALIFIED = 1;
    private static final byte TYPE_ALIAS_LIKE_REFERENCE = 2;

    public PyTargetExpressionElementType() {
        super("TARGET_EXPRESSION");
    }

    public PyTargetExpressionElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyTargetExpressionImpl(node);
    }

    @Override
    public PyTargetExpression createPsi(PyTargetExpressionStub stub) {
        return new PyTargetExpressionImpl(stub);
    }

    @Override
    public PyTargetExpressionStub createStub(PyTargetExpression psi, StubElement parentStub) {
        String name = psi.getName();
        PyExpression assignedValue = psi.findAssignedValue();
        String docString = DocStringUtil.getDocStringValue(psi);
        String typeComment = psi.getTypeCommentAnnotation();
        String annotation = psi.getAnnotationValue();
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi);

        CustomTargetExpressionStub customStub = createCustomStub(psi);
        if (customStub != null) {
            return new PyTargetExpressionStubImpl(name, docString, typeComment, annotation, psi.hasAssignedValue(), customStub, parentStub,
                                                  versions);
        }

        PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.Other;
        QualifiedName initializer = null;
        PyLiteralKind assignedLiteralKind = null;
        String assignedLiteralValue = null;
        boolean typeAliasLikeReference = false;
        Ref<QualifiedName> assignedReference = PyTargetExpressionImpl.getAssignedReferenceQualifiedName(psi);
        if (assignedReference != null) {
            initializerType = PyTargetExpressionStub.InitializerType.ReferenceExpression;
            initializer = assignedReference.get();
            typeAliasLikeReference = PyTypingAliasStubType.isTypeAliasLikeReference(psi);
        }
        else {
            Ref<QualifiedName> assignedCallCalleeReference = PyTargetExpressionImpl.getAssignedCallCalleeQualifiedName(psi);
            if (assignedCallCalleeReference != null) {
                initializerType = PyTargetExpressionStub.InitializerType.CallExpression;
                initializer = assignedCallCalleeReference.get();
            }
            else {
                assignedLiteralKind = PyLiteralKind.fromExpression(assignedValue);
                assignedLiteralValue = PyTargetExpressionImpl.getAssignedLiteralValueText(assignedValue);
            }
        }
        return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, assignedLiteralKind, assignedLiteralValue,
                                              psi.isQualified(), typeAliasLikeReference, typeComment, annotation, psi.hasAssignedValue(),
                                              parentStub, versions);
    }

    @Override
    public void serialize(PyTargetExpressionStub stub, StubOutputStream stream) throws IOException {
        stream.writeName(stub.getName());
        String docString = stub.getDocString();
        stream.writeUTFFast(docString != null ? docString : "");
        stream.writeVarInt(stub.getInitializerType().getIndex());
        stream.writeName(stub.getTypeComment());
        stream.writeName(stub.getAnnotation());
        stream.writeBoolean(stub.hasAssignedValue());
        PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), stream);
        CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
        if (customStub != null) {
            serializeCustomStub(customStub, stream);
        }
        else {
            QualifiedName.serialize(stub.getInitializer(), stream);
            byte flags = 0;
            if (stub.isQualified()) {
                flags |= QUALIFIED;
            }
            if (stub.isTypeAliasLikeReference()) {
                flags |= TYPE_ALIAS_LIKE_REFERENCE;
            }
            stream.writeByte(flags);
            PyLiteralKind.serialize(stream, stub.getAssignedLiteralKind());
            DataInputOutputUtil.writeNullable(stream, stub.getAssignedLiteralValue(), stream::writeUTFFast);
        }
    }

    @Override
    public PyTargetExpressionStub deserialize(StubInputStream stream, StubElement parentStub)
        throws IOException {
        String name = stream.readNameString();
        String docString = stream.readUTFFast();
        if (docString.isEmpty()) {
            docString = null;
        }
        PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.fromIndex(stream.readVarInt());
        String typeComment = stream.readNameString();
        String annotation = stream.readNameString();
        boolean hasAssignedValue = stream.readBoolean();
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(stream);
        if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
            CustomTargetExpressionStub stub = deserializeCustomStub(stream);
            return new PyTargetExpressionStubImpl(name, docString, typeComment, annotation, hasAssignedValue, stub, parentStub, versions);
        }
        QualifiedName initializer = QualifiedName.deserialize(stream);
        byte flags = stream.readByte();
        boolean isQualified = (flags & QUALIFIED) != 0;
        boolean typeAliasLikeReference = (flags & TYPE_ALIAS_LIKE_REFERENCE) != 0;
        PyLiteralKind literalKind = PyLiteralKind.deserialize(stream);
        var literalValue = DataInputOutputUtil.readNullable(stream, stream::readUTFFast);
        return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, literalKind, literalValue, isQualified,
                                              typeAliasLikeReference, typeComment, annotation, hasAssignedValue, parentStub, versions);
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        if (node.getTreeParent().getElementType() != PyElementTypes.ASSIGNMENT_EXPRESSION &&
            PsiTreeUtil.getParentOfType(node.getPsi(), PyComprehensionElement.class, true, PyDocStringOwner.class) != null) {
            return false;
        }
        ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
        ASTNode qualifierNode = node.findChildByType(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens());
        if (functionNode != null && qualifierNode != null) {
            if (PyUtil.isNewMethod(functionNode.getPsi())) {
                return true;
            }
            ASTNode parameterList = functionNode.findChildByType(PyElementTypes.PARAMETER_LIST);
            assert parameterList != null;
            ASTNode[] children = parameterList.getChildren(PyElementTypes.FORMAL_PARAMETER_SET);
            if (children.length == 0) {
                return false;
            }
            PyAstNamedParameter parameter = ((PyAstParameter)children[0].getPsi()).getAsNamed();
            return parameter != null && qualifierNode.getText().equals(parameter.getName());
        }
        return functionNode == null && qualifierNode == null;
    }

    @Override
    public void indexStub(PyTargetExpressionStub stub, IndexSink sink) {
        String name = stub.getName();
        if (name != null && PyUtil.getInitialUnderscores(name) == 0) {
            if (stub.getParentStub() instanceof PyFileStub) {
                sink.occurrence(PyVariableNameIndex.KEY, name);
                sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
            }
        }
        for (CustomTargetExpressionStubType stubType : getExtensions()) {
            stubType.indexStub(stub, sink);
        }
    }

    @Override
    public List<CustomTargetExpressionStubType<? extends CustomTargetExpressionStub>> getExtensions() {
        return CustomTargetExpressionStubType.EP_NAME.getExtensionList();
    }
}
