// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.*;
import com.google.common.collect.RangeSet;
import consulo.language.ast.ASTNode;
import consulo.util.lang.Version;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.impl.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.impl.psi.stubs.PySuperClassIndex;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass>
    implements PyCustomizableStubElementType<PyClass, PyCustomClassStub, PyCustomClassStubType<? extends PyCustomClassStub>> {

    public PyClassElementType() {
        this("CLASS_DECLARATION");
    }

    public PyClassElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyClassImpl(node);
    }

    @Override
    public PyClass createPsi(PyClassStub stub) {
        return new PyClassImpl(stub);
    }

    @Override
    public PyClassStub createStub(PyClass psi, StubElement parentStub) {
        return new PyClassStubImpl(psi.getName(),
                                   parentStub,
                                   getSuperClassQNames(psi),
                                   ContainerUtil.map(psi.getSuperClassExpressions(), PsiElement::getText),
                                   PyPsiUtils.asQualifiedName(psi.getMetaClassExpression()),
                                   psi.getOwnSlots(),
                                   psi.getOwnMatchArgs(),
                                   PyPsiUtils.strValue(psi.getDocStringExpression()),
                                   psi.getDeprecationMessage(),
                                   getStubElementType(),
                                   PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi),
                                   createCustomStub(psi));
    }

    public static Map<QualifiedName, QualifiedName> getSuperClassQNames(PyClass pyClass) {
        Map<QualifiedName, QualifiedName> result = new LinkedHashMap<>();

        for (PyExpression expression : PyClassImpl.getUnfoldedSuperClassExpressions(pyClass)) {
            QualifiedName importedQName = PyPsiUtils.asQualifiedName(expression);
            QualifiedName originalQName = resolveOriginalSuperClassQName(expression);

            result.put(importedQName, originalQName);
        }

        return result;
    }

    private static @Nullable QualifiedName resolveOriginalSuperClassQName(PyExpression superClassExpression) {
        if (superClassExpression instanceof PyReferenceExpression reference) {
            String referenceName = reference.getName();

            if (referenceName == null) {
                return PyPsiUtils.asQualifiedName(superClassExpression);
            }

            Optional<QualifiedName> qualifiedName = PyResolveUtil.resolveLocally(reference).stream()
                .filter(PyImportElement.class::isInstance)
                .map(PyImportElement.class::cast)
                .filter(element -> element.getAsName() != null)
                .map(PyImportElement::getImportedQName)
                .filter(Objects::nonNull)
                .findAny();

            if (qualifiedName.isPresent()) {
                return qualifiedName.get();
            }
        }

        return PyPsiUtils.asQualifiedName(superClassExpression);
    }

    @Override
    public void serialize(PyClassStub pyClassStub, StubOutputStream dataStream) throws IOException {
        dataStream.writeName(pyClassStub.getName());

        Map<QualifiedName, QualifiedName> superClasses = pyClassStub.getSuperClasses();
        dataStream.writeByte(superClasses.size());
        for (Map.Entry<QualifiedName, QualifiedName> entry : superClasses.entrySet()) {
            QualifiedName.serialize(entry.getKey(), dataStream);
            QualifiedName.serialize(entry.getValue(), dataStream);
        }

        List<String> baseClassesText = pyClassStub.getSuperClassesText();
        dataStream.writeByte(baseClassesText.size());
        for (String text : baseClassesText) {
            dataStream.writeName(text);
        }

        QualifiedName.serialize(pyClassStub.getMetaClass(), dataStream);

        PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());
        PyFileElementType.writeNullableList(dataStream, pyClassStub.getMatchArgs());

        String docString = pyClassStub.getDocString();
        dataStream.writeUTFFast(docString != null ? docString : "");
        dataStream.writeName(pyClassStub.getDeprecationMessage());

        PyVersionSpecificStubBaseKt.serializeVersions(pyClassStub.getVersions(), dataStream);

        serializeCustomStub(pyClassStub.getCustomStub(PyCustomClassStub.class), dataStream);
    }

    @Override
    public PyClassStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();

        int superClassCount = dataStream.readByte();
        Map<QualifiedName, QualifiedName> superClasses = new LinkedHashMap<>();
        for (int i = 0; i < superClassCount; i++) {
            superClasses.put(QualifiedName.deserialize(dataStream), QualifiedName.deserialize(dataStream));
        }

        byte baseClassesCount = dataStream.readByte();
        ArrayList<String> baseClassesText = new ArrayList<>();
        for (int i = 0; i < baseClassesCount; i++) {
            baseClassesText.add(dataStream.readNameString());
        }


        QualifiedName metaClass = QualifiedName.deserialize(dataStream);

        List<String> slots = PyFileElementType.readNullableList(dataStream);
        List<String> matchArgs = PyFileElementType.readNullableList(dataStream);

        String docStringInStub = dataStream.readUTFFast();
        String docString = StringUtil.nullize(docStringInStub);

        String deprecationMessage = dataStream.readNameString();

        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);

        PyCustomClassStub customStub = deserializeCustomStub(dataStream);

        return new PyClassStubImpl(name, parentStub, superClasses, baseClassesText, metaClass, slots, matchArgs, docString, deprecationMessage,
                                   getStubElementType(), versions, customStub);
    }

    @Override
    public void indexStub(PyClassStub stub, IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PyClassNameIndex.KEY, name);
            sink.occurrence(PyClassNameIndexInsensitive.KEY, StringUtil.toLowerCase(name));
            if (stub.getParentStub() instanceof PyFileStub && PyUtil.getInitialUnderscores(name) == 0) {
                sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
            }
        }

        for (String attribute : PyClassAttributesIndex.getAllDeclaredAttributeNames(stub.getPsi())) {
            sink.occurrence(PyClassAttributesIndex.KEY, attribute);
        }

        stub.getSuperClasses().values()
            .stream()
            .filter(Objects::nonNull)
            .map(QualifiedName::getLastComponent)
            .filter(Objects::nonNull)
            .forEach(className -> sink.occurrence(PySuperClassIndex.KEY, className));

        for (PyCustomClassStubType stubType : getExtensions()) {
            stubType.indexStub(stub, sink);
        }
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.CLASS_DECLARATION;
    }

    @Override
    public List<PyCustomClassStubType<? extends PyCustomClassStub>> getExtensions() {
        return PyCustomClassStubType.EP_NAME.getExtensionList();
    }
}