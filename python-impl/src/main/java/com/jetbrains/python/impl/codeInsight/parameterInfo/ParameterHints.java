// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.parameterInfo;

import consulo.language.editor.parameterInfo.SignatureStyle;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class ParameterHints {

    private final List<PyParameterInfoUtils.ParameterDescription> myParameterDescriptors;
    private final Map<Integer, EnumSet<SignatureStyle>> myFlags;

    public ParameterHints(List<PyParameterInfoUtils.ParameterDescription> parameterDescriptors, Map<Integer, EnumSet<SignatureStyle>> flags) {
        myParameterDescriptors = parameterDescriptors;
        myFlags = flags;
    }

    public List<PyParameterInfoUtils.ParameterDescription> getParameterDescriptors() {
        return myParameterDescriptors;
    }

    public Map<Integer, EnumSet<SignatureStyle>> getFlags() {
        return myFlags;
    }
}
