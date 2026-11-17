// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.parser.ParserDefinition;
import consulo.language.psi.stub.PsiFileStubImpl;
import consulo.language.psi.stub.IStubFileElementType;
import consulo.index.io.StringRef;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;

import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

public class PyFileStubImpl extends PsiFileStubImpl<PyFile> implements PyFileStub {
    private final @Nullable List<String> myDunderAll;
    private final BitSet myFutureFeatures; // stores IDs of features
    private final @Nullable StringRef myDeprecationMessage;

    private static final int FUTURE_FEATURE_SET_SIZE = 32; // 32 features is ought to be enough for everybody! all bits fit into an int.

    public PyFileStubImpl(PyFile file) {
        super(file);
        PyFileImpl fileImpl = (PyFileImpl)file;
        myFutureFeatures = new BitSet(FUTURE_FEATURE_SET_SIZE);
        myDunderAll = fileImpl.calculateDunderAll();
        for (FutureFeature fuf : FutureFeature.values()) {
            myFutureFeatures.set(fuf.ordinal(), fileImpl.calculateImportFromFuture(fuf));
        }
        String message = fileImpl.extractDeprecationMessage();
        myDeprecationMessage = message == null ? null : StringRef.fromString(message);
    }

    public PyFileStubImpl(@Nullable List<String> dunderAll, BitSet future_features, @Nullable StringRef deprecationMessage) {
        super(null);
        myDunderAll = dunderAll;
        myFutureFeatures = future_features;
        myDeprecationMessage = deprecationMessage;
    }

    @Override
    public @Nullable List<String> getDunderAll() {
        return myDunderAll;
    }

    @Override
    public BitSet getFutureFeatures() {
        return myFutureFeatures;
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        return myDeprecationMessage == null ? null : myDeprecationMessage.getString();
    }

    @Override
    public IStubFileElementType getType() {
        return (IStubFileElementType)ParserDefinition.forLanguage(PythonLanguage.getInstance()).getFileNodeType();
    }
}
