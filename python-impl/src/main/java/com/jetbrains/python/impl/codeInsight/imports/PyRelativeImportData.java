// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyFile;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author Aleksei Kniazev
 */
public class PyRelativeImportData {
    private final String myRelativeLocation;
    private final int myRelativeLevel;

    private PyRelativeImportData(String relativeLocation, int relativeLevel) {
        myRelativeLocation = relativeLocation;
        myRelativeLevel = relativeLevel;
    }

    public String getRelativeLocation() {
        return myRelativeLocation;
    }

    public int getRelativeLevel() {
        return myRelativeLevel;
    }

    public String getLocationWithDots() {
        return ".".repeat(myRelativeLevel) + myRelativeLocation;
    }

    public static @Nullable PyRelativeImportData fromString(String location, PyFile file) {
        QualifiedName qName = QualifiedName.fromDottedString(location);
        QualifiedName fileQName = QualifiedNameFinder.findCanonicalImportPath(file, null);
        if (fileQName == null) {
            return null;
        }

        if (!java.util.Objects.equals(qName.getFirstComponent(), fileQName.getFirstComponent())) {
            return null;
        }

        List<String> fileComponents = fileQName.getComponents();
        List<String> components = qName.getComponents();
        int common = 0;
        while (common < fileComponents.size() && common < components.size() &&
            fileComponents.get(common).equals(components.get(common))) {
            common++;
        }

        QualifiedName remainingFileQname = fileQName.removeHead(common);
        QualifiedName remainingQname = qName.removeHead(common);
        int implicitLevel = PyNames.INIT_DOT_PY.equals(file.getName()) ? 1 : 0;

        return new PyRelativeImportData(remainingQname.toString(), remainingFileQname.getComponentCount() + implicitLevel);
    }
}
