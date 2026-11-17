// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.attach;

import consulo.execution.attach.XAttachProcessPresentationGroup;
import consulo.platform.ProcessInfo;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.image.Image;
import consulo.util.dataholder.UserDataHolder;

final class PyLocalAttachGroup implements XAttachProcessPresentationGroup {
    public static final PyLocalAttachGroup INSTANCE = new PyLocalAttachGroup();

    private PyLocalAttachGroup() {
    }

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public String getGroupName() {
        return PyLocalize.pythonLocalAttachGroupName().get();
    }

    @Override
    public Image getItemIcon(Project project, ProcessInfo info, UserDataHolder dataHolder) {
        return PlatformIconGroup.runconfigurationsApplication();
    }

    @Override
    public String getItemDisplayText(Project project, ProcessInfo info, UserDataHolder dataHolder) {
        return info.getArgs();
    }
}
