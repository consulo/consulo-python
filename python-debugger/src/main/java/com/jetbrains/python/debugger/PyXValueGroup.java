// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import consulo.execution.debug.frame.XValueGroup;
import consulo.localize.LocalizeValue;

public class PyXValueGroup extends XValueGroup {
    private final ProcessDebugger.GROUP_TYPE myGroupType;

    public PyXValueGroup(LocalizeValue groupName, ProcessDebugger.GROUP_TYPE groupType) {
        super(groupName);
        myGroupType = groupType;
    }

    public ProcessDebugger.GROUP_TYPE getGroupType() {
        return myGroupType;
    }
}
