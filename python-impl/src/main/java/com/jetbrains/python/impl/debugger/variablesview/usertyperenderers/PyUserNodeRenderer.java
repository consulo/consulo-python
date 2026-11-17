// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers;

import com.jetbrains.python.debugger.PyUserTypeRenderer;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PyUserNodeRenderer {
    private boolean myEnabled;
    private String myName;
    private String myToType = "object";
    private String myTypeCanonicalImportPath = "";
    private String myTypeQualifiedName = "";
    private String myTypeSourceFile = "";
    private boolean myModuleRootHasOneTypeWithSameName = false;
    private final PyNodeValueRenderer myValueRenderer = new PyNodeValueRenderer();
    private final PyNodeChildrenRenderer myChildrenRenderer = new PyNodeChildrenRenderer();

    public PyUserNodeRenderer(boolean isEnabled, @Nullable List<String> existingNames) {
        myEnabled = isEnabled;
        myName = getNewRendererName(existingNames);
    }

    public boolean isEnabled() {
        return myEnabled;
    }

    public void setEnabled(boolean enabled) {
        myEnabled = enabled;
    }

    public String getName() {
        return myName;
    }

    public void setName(String name) {
        myName = name;
    }

    public String getToType() {
        return myToType;
    }

    public void setToType(String toType) {
        myToType = toType;
    }

    public String getTypeCanonicalImportPath() {
        return myTypeCanonicalImportPath;
    }

    public void setTypeCanonicalImportPath(String typeCanonicalImportPath) {
        myTypeCanonicalImportPath = typeCanonicalImportPath;
    }

    public String getTypeQualifiedName() {
        return myTypeQualifiedName;
    }

    public void setTypeQualifiedName(String typeQualifiedName) {
        myTypeQualifiedName = typeQualifiedName;
    }

    public String getTypeSourceFile() {
        return myTypeSourceFile;
    }

    public void setTypeSourceFile(String typeSourceFile) {
        myTypeSourceFile = typeSourceFile;
    }

    public boolean getModuleRootHasOneTypeWithSameName() {
        return myModuleRootHasOneTypeWithSameName;
    }

    public void setModuleRootHasOneTypeWithSameName(boolean moduleRootHasOneTypeWithSameName) {
        myModuleRootHasOneTypeWithSameName = moduleRootHasOneTypeWithSameName;
    }

    public PyNodeValueRenderer getValueRenderer() {
        return myValueRenderer;
    }

    public PyNodeChildrenRenderer getChildrenRenderer() {
        return myChildrenRenderer;
    }

    @Override
    public PyUserNodeRenderer clone() {
        PyUserNodeRenderer renderer = new PyUserNodeRenderer(myEnabled, null);
        renderer.myName = myName;
        renderer.myToType = myToType;
        renderer.myTypeCanonicalImportPath = myTypeCanonicalImportPath;
        renderer.myTypeQualifiedName = myTypeQualifiedName;
        renderer.myTypeSourceFile = myTypeSourceFile;
        renderer.myModuleRootHasOneTypeWithSameName = myModuleRootHasOneTypeWithSameName;
        renderer.myValueRenderer.setDefault(myValueRenderer.isDefault());
        renderer.myValueRenderer.setExpression(myValueRenderer.getExpression());
        renderer.myChildrenRenderer.setDefault(myChildrenRenderer.isDefault());
        renderer.myChildrenRenderer.setChildren(myChildrenRenderer.getChildren());
        renderer.myChildrenRenderer.setAppendDefaultChildren(myChildrenRenderer.getAppendDefaultChildren());
        return renderer;
    }

    public static class PyNodeValueRenderer implements Serializable {
        private boolean myDefault = true;
        private String myExpression = "";

        public boolean isDefault() {
            return myDefault;
        }

        public void setDefault(boolean isDefault) {
            myDefault = isDefault;
        }

        public String getExpression() {
            return myExpression;
        }

        public void setExpression(String expression) {
            myExpression = expression;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PyNodeValueRenderer that && myDefault == that.myDefault && myExpression.equals(that.myExpression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myDefault, myExpression);
        }
    }

    public static class PyNodeChildrenRenderer implements Serializable {
        private boolean myDefault = true;
        private List<ChildInfo> myChildren = List.of();
        private boolean myAppendDefaultChildren = false;

        public boolean isDefault() {
            return myDefault;
        }

        public void setDefault(boolean isDefault) {
            myDefault = isDefault;
        }

        public List<ChildInfo> getChildren() {
            return myChildren;
        }

        public void setChildren(List<ChildInfo> children) {
            myChildren = children;
        }

        public boolean getAppendDefaultChildren() {
            return myAppendDefaultChildren;
        }

        public void setAppendDefaultChildren(boolean appendDefaultChildren) {
            myAppendDefaultChildren = appendDefaultChildren;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PyNodeChildrenRenderer that &&
                myDefault == that.myDefault &&
                myAppendDefaultChildren == that.myAppendDefaultChildren &&
                myChildren.equals(that.myChildren);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myDefault, myChildren, myAppendDefaultChildren);
        }
    }

    public static class ChildInfo implements Serializable {
        private String myExpression;

        public ChildInfo() {
            this("");
        }

        public ChildInfo(String expression) {
            myExpression = expression;
        }

        public String getExpression() {
            return myExpression;
        }

        public void setExpression(String expression) {
            myExpression = expression;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ChildInfo that && myExpression.equals(that.myExpression);
        }

        @Override
        public int hashCode() {
            return myExpression.hashCode();
        }
    }

    public boolean isDefault() {
        return myValueRenderer.isDefault() && myChildrenRenderer.isDefault();
    }

    private boolean hasNoEmptyTypeInfo() {
        return !myTypeQualifiedName.equals("") || !myTypeCanonicalImportPath.equals("");
    }

    public boolean isApplicable() {
        return hasNoEmptyTypeInfo() && myEnabled;
    }

    public boolean equalTo(PyUserNodeRenderer other) {
        return other.myEnabled == myEnabled &&
            other.myName.equals(myName) &&
            other.myToType.equals(myToType) &&
            other.myValueRenderer.equals(myValueRenderer) &&
            other.myChildrenRenderer.equals(myChildrenRenderer);
    }

    @Override
    public String toString() {
        return myName;
    }

    public PyUserTypeRenderer convertRenderer() {
        List<PyUserTypeRenderer.ChildInfo> children = new ArrayList<>();
        for (ChildInfo child : myChildrenRenderer.getChildren()) {
            children.add(new PyUserTypeRenderer.ChildInfo(child.getExpression()));
        }
        return new PyUserTypeRenderer(
            myToType,
            myTypeCanonicalImportPath,
            myTypeQualifiedName,
            myTypeSourceFile,
            myModuleRootHasOneTypeWithSameName,
            myValueRenderer.isDefault(),
            myValueRenderer.getExpression(),
            myChildrenRenderer.isDefault(),
            myChildrenRenderer.getAppendDefaultChildren(),
            children
        );
    }

    public static String getNewRendererName(@Nullable List<String> existingNames) {
        String defaultName = PyLocalize.formDebuggerVariablesViewUserTypeRenderersUnnamed().get();
        if (existingNames == null || existingNames.isEmpty()) {
            return defaultName;
        }
        long duplicatedNames = existingNames.stream().filter(it -> it.startsWith(defaultName)).count();
        if (duplicatedNames > 0) {
            return defaultName + " (" + duplicatedNames + ")";
        }
        else {
            return defaultName;
        }
    }
}
