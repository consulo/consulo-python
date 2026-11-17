/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.structureView;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.navigation.ItemPresentation;
import consulo.ui.ex.ColoredItemPresentation;
import consulo.ui.image.Image;
import com.jetbrains.python.impl.PythonIcons;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Handles nodes in Structure View.
 *
 * @author yole
 */
public class PyStructureViewElement implements StructureViewTreeElement {
    protected enum Visibility {
        NORMAL, // visible
        INVISIBLE, // not visible: e.g. local to function
        PRIVATE,  // "__foo" in a class
        PROTECTED, // "_foo"
        PREDEFINED // like "__init__"; only if really visible
    }

    private final PyElement myElement;
    private final Visibility myVisibility;
    private @Nullable Image myIcon;
    private final boolean myInherited;
    private final boolean myField;

    protected PyStructureViewElement(PyElement element, Visibility visibility, boolean inherited, boolean field) {
        myElement = element;
        myVisibility = visibility;
        myInherited = inherited;
        myField = field;
    }

    public PyStructureViewElement(PyElement element) {
        this(element, Visibility.NORMAL, false, false);
    }

    protected StructureViewTreeElement createChild(PyElement element, Visibility visibility, boolean inherited, boolean field) {
        return new PyStructureViewElement(element, visibility, inherited, field);
    }

    @Override
    public @Nullable PyElement getValue() {
        return myElement.isValid() ? myElement : null;
    }

    public boolean isInherited() {
        return myInherited;
    }

    public boolean isField() {
        return myField;
    }

    @Override
    @RequiredReadAction
    public void navigate(boolean requestFocus) {
        PyElement element = getValue();
        if (element != null) {
            myElement.navigate(requestFocus);
        }
    }

    @Override
    @RequiredReadAction
    public boolean canNavigate() {
        return myElement.isValid() && myElement.canNavigate();
    }

    @Override
    @RequiredReadAction
    public boolean canNavigateToSource() {
        return myElement.isValid() && myElement.canNavigateToSource();
    }

    public void setIcon(Image icon) {
        myIcon = icon;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructureViewTreeElement) {
            Object value = ((StructureViewTreeElement) o).getValue();
            String name = myElement.getName();
            if (value instanceof PyElement && name != null) {
                return name.equals(((PyElement) value).getName());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        String name = myElement.getName();
        return name != null ? name.hashCode() : 0;
    }

    @Override
    @RequiredReadAction
    public StructureViewTreeElement[] getChildren() {
        PyElement element = getValue();
        if (element == null) {
            return EMPTY_ARRAY;
        }

        Collection<StructureViewTreeElement> children = new ArrayList<>();
        for (PyElement e : getElementChildren(element)) {
            children.add(createChild(e, getElementVisibility(e), myInherited, elementIsField(e)));
        }
        PyPsiUtils.assertValid(element);
        if (element instanceof PyClass) {
            TypeEvalContext context = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
            for (PyClass c : ((PyClass) element).getAncestorClasses(context)) {
                for (PyElement e : getElementChildren(c)) {
                    StructureViewTreeElement inherited = createChild(e, getElementVisibility(e), true, elementIsField(e));
                    if (!children.contains(inherited)) {
                        children.add(inherited);
                    }
                }
            }
        }
        return children.toArray(StructureViewTreeElement.EMPTY_ARRAY);
    }

    protected boolean elementIsField(PyElement element) {
        return element instanceof PyTargetExpression && PsiTreeUtil.getParentOfType(element, PyClass.class) != null;
    }

    private static Visibility getElementVisibility(PyElement element) {
        if (!(element instanceof PyTargetExpression) && PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
            return Visibility.INVISIBLE;
        }
        else {
            return getVisibilityByName(element.getName());
        }
    }

    @RequiredReadAction
    private Collection<PyElement> getElementChildren(PyElement element) {
        Collection<PyElement> children = new LinkedHashSet<>();
        PyPsiUtils.assertValid(element);
        element.acceptChildren(new PyElementVisitor() {
            @Override
            public void visitElement(PsiElement e) {
                if (isWorthyItem(e, element)) {
                    children.add((PyElement) e);
                }
                else {
                    e.acceptChildren(this);
                }
            }
        });
        if (element instanceof PyClass) {
            List<PyElement> attrs = getClassAttributes((PyClass) element);
            List<PyElement> filtered = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (PyElement attr : attrs) {
                String name = attr.getName();
                PyPsiUtils.assertValid(attr);
                if (!names.contains(name)) {
                    filtered.add(attr);
                }
                names.add(name);
            }
            Comparator<PyElement> comparator = (e1, e2) -> {
                String n1 = e1.getName();
                String n2 = e2.getName();
                return (n1 != null && n2 != null) ? n1.compareTo(n2) : 0;
            };
            filtered.sort(comparator);
            children.addAll(filtered);
        }
        return children;
    }

    protected List<PyElement> getClassAttributes(PyClass cls) {
        List<PyElement> results = new ArrayList<>();
        results.addAll(cls.getInstanceAttributes());
        results.addAll(cls.getClassAttributes());
        return results;
    }

    private static Visibility getVisibilityByName(@Nullable String name) {
        if (name != null) {
            if (name.startsWith("__")) {
                if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name)) {
                    return Visibility.PREDEFINED;
                }
                else {
                    return Visibility.PRIVATE;
                }
            }
            else if (name.startsWith("_")) {
                return Visibility.PROTECTED;
            }
        }
        return Visibility.NORMAL;
    }

    protected boolean isWorthyItem(@Nullable PsiElement element, @Nullable PsiElement parent) {
        if (element instanceof PyClass || element instanceof PyFunction || element instanceof PyTypeAliasStatement) {
            return true;
        }
        if (!(parent instanceof PyClass) && element instanceof PyTargetExpression target && !target.isQualified()) {
            PsiElement e = element.getParent();
            if (e instanceof PyAssignmentStatement assignment) {
                e = assignment.getParent();
                if (e instanceof PyFile) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ItemPresentation getPresentation() {
        PyElement element = getValue();
        ItemPresentation presentation = element != null ? element.getPresentation() : null;

        return new ColoredItemPresentation() {
            @Override
            public LocalizeValue getPresentableText() {
                if (element instanceof PyFile) {
                    return LocalizeValue.ofNullable(element.getName());
                }
                return presentation != null ? presentation.getPresentableText() : LocalizeValue.of(PyNames.UNNAMED_ELEMENT);
            }

            @Nullable
            @Override
            public TextAttributesKey getTextAttributesKey() {
                if (isInherited()) {
                    return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
                }
                return null;
            }

            @Nullable
            @Override
            public String getLocationString() {
                return null;
            }

            @Override
            @RequiredReadAction
            public @Nullable Image getIcon() {
                if (element == null) {
                    return null;
                }

                Image normalIcon = IconDescriptorUpdaters.getIcon(element, 0);
                if (myIcon != null) {
                    normalIcon = myIcon; // override normal
                }
                if (myVisibility == Visibility.NORMAL) {
                    return normalIcon;
                }
                else {
                    IconDescriptor iconDescriptor = new IconDescriptor(normalIcon);

                    Image overlay = null;
                    if (myVisibility == Visibility.PRIVATE || myVisibility == Visibility.PROTECTED) {
                        overlay = PythonIcons.Python.Nodes.Lock;
                    }
                    else if (myVisibility == Visibility.PREDEFINED) {
                        overlay = PythonIcons.Python.Nodes.Cyan_dot;
                    }
                    else if (myVisibility == Visibility.INVISIBLE) {
                        overlay = PythonIcons.Python.Nodes.Red_inv_triangle;
                    }
                    if (overlay != null) {
                        iconDescriptor.addLayerIcon(overlay);
                    }
                    return iconDescriptor.toIcon();
                }
            }
        };
    }
}
