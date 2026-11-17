// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.*;
import com.google.common.collect.RangeSet;
import consulo.util.lang.Version;
import consulo.util.lang.StringUtil;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ObjectUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PyClassStubImpl extends PyVersionSpecificStubBase<PyClass> implements PyClassStub {

    private final @Nullable String myName;

    private final Map<QualifiedName, QualifiedName> mySuperClasses;

    private final @Nullable QualifiedName myMetaClass;

    private final @Nullable List<String> mySlots;

    private final @Nullable List<String> myMatchArgs;

    private final @Nullable String myDocString;

    private final List<String> mySuperClassesText;

    private final @Nullable String myDeprecationMessage;

    private final @Nullable PyCustomClassStub myCustomStub;

    public PyClassStubImpl(@Nullable String name,
                           @Nullable StubElement parentStub,
                           Map<QualifiedName, QualifiedName> superClasses,
                           List<String> superClassesText,
                           @Nullable QualifiedName metaClass,
                           @Nullable List<String> slots,
                           @Nullable List<String> matchArgs,
                           @Nullable String docString,
                           @Nullable String deprecationMessage,
                           IStubElementType stubElementType,
                           RangeSet<Version> versions,
                           @Nullable PyCustomClassStub customStub) {
        super(parentStub, stubElementType, versions);
        myName = name;
        mySuperClasses = superClasses;
        mySuperClassesText = superClassesText;
        myMetaClass = metaClass;
        mySlots = slots;
        myMatchArgs = matchArgs;
        myDocString = docString;
        myDeprecationMessage = deprecationMessage;
        myCustomStub = customStub;
    }

    @Override
    public @Nullable String getName() {
        return myName;
    }

    @Override
    public Map<QualifiedName, QualifiedName> getSuperClasses() {
        return mySuperClasses;
    }

    @Override
    public @Nullable QualifiedName getMetaClass() {
        return myMetaClass;
    }

    @Override
    public @Nullable List<String> getSlots() {
        return mySlots;
    }

    @Override
    public @Nullable List<String> getMatchArgs() {
        return myMatchArgs;
    }

    @Override
    public @Nullable String getDocString() {
        return myDocString;
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        return myDeprecationMessage;
    }

    @Override
    public List<String> getSuperClassesText() {
        return mySuperClassesText;
    }

    @Override
    public <T> @Nullable T getCustomStub(Class<T> stubClass) {
        return ObjectUtil.tryCast(myCustomStub, stubClass);
    }

    @Override
    public String toString() {
        // @formatter:off
        return "PyClassStubImpl{" +
               "myName='" + myName + '\'' +
               ", mySuperClasses=" + mySuperClasses +
               ", myMetaClass=" + myMetaClass +
               ", mySlots=" + mySlots +
               ", myMatchArgs=" + myMatchArgs +
               ", myDocString='" + (myDocString != null ? StringUtil.escapeStringCharacters(myDocString) : null) + '\'' +
               ", mySuperClassesText=" + mySuperClassesText +
               ", myDeprecationMessage='" + (myDeprecationMessage != null ? StringUtil.escapeStringCharacters((myDeprecationMessage)) : null) + '\'' +
               ", myCustomStub=" + myCustomStub +
               '}';
        // @formatter:on
    }
}