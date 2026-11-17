// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import consulo.usage.UsageInfo;
import com.jetbrains.python.psi.PyClass;

/**
 * TODO: Make it generic to allow to reuse in another projects?
 * Usage info that displays destination (where should member be moved)
 *
 * @author Ilya.Kazakevich
 */
public final class PyUsageInfo extends UsageInfo {
    private final PyClass myTo;

    PyUsageInfo(PyClass to) {
        super(to, true); //TODO: Make super generic and get rid of field?
        myTo = to;
    }

    public PyClass getTo() {
        return myTo;
    }
}
