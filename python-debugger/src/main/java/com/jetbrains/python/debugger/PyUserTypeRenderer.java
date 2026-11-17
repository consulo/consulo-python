// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import java.util.List;

public class PyUserTypeRenderer {
    private final String myToType;
    private final String myTypeCanonicalImportPath;
    private final String myTypeQualifiedName;
    private final String myTypeSourceFile;
    private final boolean myModuleRootHasOneTypeWithSameName;
    private final boolean myIsDefaultValueRenderer;
    private final String myExpression;
    private final boolean myIsDefaultChildrenRenderer;
    private final boolean myIsAppendDefaultChildren;
    private final List<ChildInfo> myChildren;

    public PyUserTypeRenderer(String toType,
                              String typeCanonicalImportPath,
                              String typeQualifiedName,
                              String typeSourceFile,
                              boolean moduleRootHasOneTypeWithSameName,
                              boolean isDefaultValueRenderer,
                              String expression,
                              boolean isDefaultChildrenRenderer,
                              boolean isAppendDefaultChildren,
                              List<ChildInfo> children) {
        myToType = toType;
        myTypeCanonicalImportPath = typeCanonicalImportPath;
        myTypeQualifiedName = typeQualifiedName;
        myTypeSourceFile = typeSourceFile;
        myModuleRootHasOneTypeWithSameName = moduleRootHasOneTypeWithSameName;
        myIsDefaultValueRenderer = isDefaultValueRenderer;
        myExpression = expression;
        myIsDefaultChildrenRenderer = isDefaultChildrenRenderer;
        myIsAppendDefaultChildren = isAppendDefaultChildren;
        myChildren = children;
    }

    public String getToType() {
        return myToType;
    }

    public String getTypeCanonicalImportPath() {
        return myTypeCanonicalImportPath;
    }

    public String getTypeQualifiedName() {
        return myTypeQualifiedName;
    }

    public String getTypeSourceFile() {
        return myTypeSourceFile;
    }

    public boolean getModuleRootHasOneTypeWithSameName() {
        return myModuleRootHasOneTypeWithSameName;
    }

    public boolean isDefaultValueRenderer() {
        return myIsDefaultValueRenderer;
    }

    public String getExpression() {
        return myExpression;
    }

    public boolean isDefaultChildrenRenderer() {
        return myIsDefaultChildrenRenderer;
    }

    public boolean isAppendDefaultChildren() {
        return myIsAppendDefaultChildren;
    }

    public List<ChildInfo> getChildren() {
        return myChildren;
    }

    public record ChildInfo(String expression) {
        public String getExpression() {
            return expression;
        }
    }
}
