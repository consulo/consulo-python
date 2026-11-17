// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import consulo.annotation.access.RequiredReadAction;

import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByType;

public interface PyAstFStringFragmentFormatPart extends PyAstElement {
    @RequiredReadAction
    default List<? extends PyAstFStringFragment> getFragments() {
        return findChildrenByType(this, PyElementTypes.FSTRING_FRAGMENT);
    }
}
