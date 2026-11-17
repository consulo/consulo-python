// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyClass;
import consulo.language.psi.stub.NamedStub;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface PyClassStub extends NamedStub<PyClass>, PyVersionSpecificStub {

    /**
     * @return a {@code Map} which contains imported class names as keys and their original names as values.
     * <i>Note: the returned {@code Map} could contain nulls as keys and as values.</i>
     */
    Map<QualifiedName, QualifiedName> getSuperClasses();

    @Nullable
    QualifiedName getMetaClass();

    @Nullable
    List<String> getSlots();

    @Nullable
    List<String> getMatchArgs();

    @Nullable
    String getDocString();

    @Nullable
    String getDeprecationMessage();

    /**
     * @return literal text of expressions in the base classes list.
     */
    List<String> getSuperClassesText();


    default <T> @Nullable T getCustomStub(Class<T> stubClass) {
        return null;
    }
}
