// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.render;


public class DecimalRenderer extends AbstractIntegerRenderer {

    private static final String myName = "Decimal";

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public String render(String value) {
        return value;
    }
}
