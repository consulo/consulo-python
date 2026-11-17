// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import consulo.execution.debug.XSourcePosition;
import org.jspecify.annotations.Nullable;


public interface PyPositionConverter {
    @Deprecated
    PySourcePosition create(String file, int line);

    /**
     * @param file filepath in position on Python side
     * @param line line number on Python side
     * @return position in Python file which is used when showing Frames window and suspended position. It must be compatible with
     * Python side, but also navigatable in the IDE editor.
     */
    default PySourcePosition convertPythonToFrame(String file, int line) {
        return create(file, line);
    }

    /**
     * @param position shown in Frames window
     * @return position on Python side
     */
    default PySourcePosition convertFrameToPython(PySourcePosition position) {
        return position;
    }

    /**
     * @param position source position in the IDE editor
     * @return position on Python side
     */
    PySourcePosition convertToPython(XSourcePosition position);

    /**
     * @param position on Python side
     * @return position in the IDE editor
     */
    default @Nullable XSourcePosition convertFromPython(PySourcePosition position) {
        return null;
    }

    default @Nullable XSourcePosition convertFromPython(PySourcePosition position, String frameName) {
        return convertFromPython(position);
    }

    PySignature convertSignature(PySignature signature);
}
