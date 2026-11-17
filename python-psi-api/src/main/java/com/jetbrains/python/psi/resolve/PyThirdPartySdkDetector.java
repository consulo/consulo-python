// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

/**
 * Marks resolved element as part of a third-party sdk, like Google App Engine.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyThirdPartySdkDetector {

    ExtensionPointName<PyThirdPartySdkDetector> EP_NAME = ExtensionPointName.create(PyThirdPartySdkDetector.class);

    boolean isInThirdPartySdk(PsiElement element);
}
