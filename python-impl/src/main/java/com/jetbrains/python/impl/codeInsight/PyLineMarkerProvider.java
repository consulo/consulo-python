// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.query.CollectionQuery;
import consulo.application.util.query.Query;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.util.collection.MultiMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@ExtensionImpl
public final class PyLineMarkerProvider implements LineMarkerProvider, PyLineSeparatorUtil.Provider {

    private static final class TooltipProvider implements Function<PsiElement, String> {
        private final String myText;

        private TooltipProvider(String text) {
            myText = text;
        }

        @Override
        public String apply(PsiElement psiElement) {
            return myText;
        }
    }

    private static final int INHERITORS_LIMIT = 10;

    private static final Function<PsiElement, String> ourSubclassTooltipProvider = identifier -> {
        PsiElement parent = identifier.getParent();
        if (!(parent instanceof PyClass pyClass)) {
            return null;
        }
        HtmlBuilder builder = new HtmlBuilder();
        builder.append(PyCodeInsightLocalize.lineMarkersTooltipHeaderIsSubclassedBy().get());
        AtomicInteger count = new AtomicInteger();
        PyClassInheritorsSearch.search(pyClass, true).forEach(inheritor -> {
            String className = inheritor.getName();
            if (className == null) {
                return true;
            }
            if (count.incrementAndGet() >= INHERITORS_LIMIT) {
                return false;
            }
            builder.br().nbsp(2).append(className);
            return true;
        });
        boolean tooManySubclasses = count.get() >= INHERITORS_LIMIT;
        return tooManySubclasses
            ? PyCodeInsightLocalize.lineMarkersTooltipHasSubclasses().get()
            : builder.wrapWithHtmlBody().toString();
    };

    private static final Function<PsiElement, String> ourOverridingMethodTooltipProvider = element -> {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PyFunction pyFunction)) {
            return "";
        }
        HtmlBuilder builder = new HtmlBuilder();
        builder.append(PyCodeInsightLocalize.lineMarkersTooltipHeaderIsOverriddenIn().get());
        AtomicInteger count = new AtomicInteger();

        PyClassInheritorsSearch.search(pyFunction.getContainingClass(), true).forEach(pyClass -> {
            String className = pyClass.getName();
            if (className == null) {
                return true;
            }
            if (count.incrementAndGet() >= INHERITORS_LIMIT) {
                return false;
            }
            if (pyClass.findMethodByName(pyFunction.getName(), false, null) != null) {
                builder.br().nbsp(2).append(className);
            }
            return true;
        });
        boolean tooManyOverrides = count.get() >= INHERITORS_LIMIT;
        return tooManyOverrides
            ? PyCodeInsightLocalize.lineMarkersTooltipHasOverriddenMethods().get()
            : builder.wrapWithHtmlBody().toString();
    };

    private static final PyLineMarkerNavigator<PsiElement> SUPER_METHOD_NAVIGATOR = new PyLineMarkerNavigator<>() {
        @Override
        protected LocalizeValue getTitle(PsiElement nameIdentifier) {
            return PyCodeInsightLocalize.lineMarkersPopupTitleChooseSuperMethod(((PyFunction) nameIdentifier.getParent()).getName());
        }

        @Override
        protected @Nullable Query<PsiElement> search(PsiElement nameIdentifier, TypeEvalContext context) {
            if (!(nameIdentifier.getParent() instanceof PyFunction)) {
                return null;
            }
            return PySuperMethodsSearch.search((PyFunction) nameIdentifier.getParent(), context);
        }
    };

    private static final PyLineMarkerNavigator<PsiElement> SUPER_ATTRIBUTE_NAVIGATOR = new PyLineMarkerNavigator<>() {
        @Override
        protected LocalizeValue getTitle(PsiElement nameIdentifier) {
            return PyCodeInsightLocalize.lineMarkersPopupTitleChooseSuperAttribute(
                ((PyTargetExpression) nameIdentifier.getParent()).getName()
            );
        }

        @Override
        protected @Nullable Query<PsiElement> search(PsiElement nameIdentifier, TypeEvalContext context) {
            if (!(nameIdentifier.getParent() instanceof PyTargetExpression)) {
                return null;
            }
            List<PsiElement> result = new ArrayList<>();
            PyClass containingClass = PsiTreeUtil.getParentOfType(nameIdentifier, PyClass.class);
            if (containingClass != null) {
                for (PyClass ancestor : containingClass.getAncestorClasses(context)) {
                    PyTargetExpression attribute = ancestor.findClassAttribute(nameIdentifier.getText(), false, context);
                    if (attribute != null) {
                        result.add(attribute);
                    }
                }
            }
            return new CollectionQuery<>(result);
        }
    };

    private static final PyLineMarkerNavigator<PsiElement> ourSubclassNavigator = new PyLineMarkerNavigator<>() {
        @Override
        protected LocalizeValue getTitle(PsiElement elt) {
            PsiElement parent = elt.getParent();
            return parent instanceof PyClass
                ? PyCodeInsightLocalize.lineMarkersPopupTitleChooseSubclass(((PyClass) parent).getName())
                : LocalizeValue.empty();
        }

        @Override
        protected @Nullable Query<? extends PsiElement> search(PsiElement elt, TypeEvalContext context) {
            PsiElement parent = elt.getParent();
            return parent instanceof PyClass ? PyClassInheritorsSearch.search((PyClass) parent, true) : null;
        }
    };

    private static final PyLineMarkerNavigator<PsiElement> ourOverridingMethodNavigator = new PyLineMarkerNavigator<>() {
        @Override
        protected LocalizeValue getTitle(PsiElement element) {
            PsiElement parent = element.getParent();
            if (parent instanceof PyFunction) {
                return PyCodeInsightLocalize.lineMarkersPopupTitleChooseOverridingMethod(((PyFunction) parent).getName());
            }
            return LocalizeValue.empty();
        }

        @Override
        protected @Nullable Query<? extends PsiElement> search(PsiElement element, TypeEvalContext context) {
            PsiElement parent = element.getParent();
            return parent instanceof PyFunction ? PyOverridingMethodsSearch.search((PyFunction) parent, true) : null;
        }
    };

    @Override
    @RequiredReadAction
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        IElementType elementType = element.getNode().getElementType();
        if (elementType == PyTokenTypes.IDENTIFIER && element.getParent() instanceof PyFunction function) {
            return getMethodMarker(element, function);
        }
        if (element instanceof PyTargetExpression && PyUtil.isClassAttribute(element)) {
            return getAttributeMarker((PyTargetExpression) element);
        }

        // Separators are registered only on the first leaf element of a declaration
        if (!DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS || element.getFirstChild() != null) {
            return null;
        }
        PyElement parentDeclaration = PsiTreeUtil.getParentOfType(element, PyFunction.class, PyClass.class);
        if (parentDeclaration == null || element != PsiTreeUtil.getDeepestFirst(parentDeclaration)) {
            return null;
        }
        if (isSeparatorAllowed(parentDeclaration)) {
            return PyLineSeparatorUtil.addLineSeparatorIfNeeded(this, parentDeclaration);
        }
        return null;
    }

    @Override
    public boolean isSeparatorAllowed(@Nullable PsiElement element) {
        // TODO consulo: BackedVirtualFile (notebook editor) missing,
        //  JB also returns false when element.getContainingFile().getVirtualFile() instanceof BackedVirtualFile
        if (element == null) {
            return false;
        }

        if (element instanceof PyClass) {
            return PyUtil.isTopLevel(element);
        }
        else if (element instanceof PyFunction) {
            if (PyUtil.isTopLevel(element)) {
                return true;
            }
            PyClass containingClass = ((PyFunction) element).getContainingClass();
            if (containingClass != null && PyUtil.isTopLevel(containingClass)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable LineMarkerInfo<PsiElement> getMethodMarker(PsiElement identifier, PyFunction function) {
        if (PyUtil.isConstructorLikeMethod(function)) {
            return null;
        }
        TypeEvalContext context = TypeEvalContext.codeAnalysis(identifier.getProject(), function.getContainingFile());
        PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
        if (superMethod != null) {
            PyClass superClass = null;
            if (superMethod instanceof PyFunction) {
                superClass = ((PyFunction) superMethod).getContainingClass();
            }
            // TODO: show "implementing" instead of "overriding" icon for Python implementations of Java interface methods
            return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                PlatformIconGroup.gutterOverridingmethod(),
                Pass.LINE_MARKERS,
                superClass == null
                    ? null
                    : new TooltipProvider(PyCodeInsightLocalize.lineMarkersTooltipOverridesMethodInClass(superClass.getName()).get()),
                SUPER_METHOD_NAVIGATOR,
                GutterIconRenderer.Alignment.RIGHT
            );
        }
        return null;
    }

    private static @Nullable LineMarkerInfo<PsiElement> getAttributeMarker(PyTargetExpression element) {
        String name = element.getReferencedName();
        if (name == null) {
            return null;
        }
        PyClass containingClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        if (containingClass == null) {
            return null;
        }
        for (PyClass ancestor : containingClass
            .getAncestorClasses(TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()))) {
            PyTargetExpression ancestorAttr = ancestor.findClassAttribute(name, false, null);
            if (ancestorAttr != null) {
                PsiElement identifier = element.getNameIdentifier();
                if (identifier != null) {
                    return new LineMarkerInfo<>(
                        identifier,
                        identifier.getTextRange(),
                        PlatformIconGroup.gutterOverridingmethod(),
                        Pass.LINE_MARKERS,
                        new TooltipProvider(PyCodeInsightLocalize.lineMarkersTooltipOverridesAttributeInClass(ancestor.getName()).get()),
                        SUPER_ATTRIBUTE_NAVIGATOR,
                        GutterIconRenderer.Alignment.RIGHT
                    );
                }
            }
        }
        return null;
    }

    @Override
    public void collectSlowLineMarkers(List<PsiElement> elements, Collection<LineMarkerInfo> result) {
        Set<PyFunction> functions = new HashSet<>();
        for (PsiElement element : elements) {
            if (element instanceof PyClass) {
                collectInheritingClasses((PyClass) element, result);
            }
            else if (element instanceof PyFunction) {
                functions.add((PyFunction) element);
            }
        }
        collectOverridingMethods(functions, result);
    }

    private static void collectInheritingClasses(PyClass element, Collection<LineMarkerInfo> result) {
        if (PyClassInheritorsSearch.search(element, false).findFirst() != null) {
            PsiElement identifier = element.getNameIdentifier();
            if (identifier != null) {
                result.add(new LineMarkerInfo<>(
                    identifier,
                    identifier.getTextRange(),
                    PlatformIconGroup.gutterOverridenmethod(),
                    Pass.LINE_MARKERS,
                    ourSubclassTooltipProvider,
                    ourSubclassNavigator,
                    GutterIconRenderer.Alignment.RIGHT
                ));
            }
        }
    }

    private static void collectOverridingMethods(Set<? extends PyFunction> functions, Collection<LineMarkerInfo> result) {
        Set<PyClass> classes = new HashSet<>();
        MultiMap<PyClass, PyFunction> candidates = new MultiMap<>();
        for (PyFunction function : functions) {
            PyClass pyClass = function.getContainingClass();
            if (pyClass != null && function.getName() != null) {
                classes.add(pyClass);
                candidates.putValue(pyClass, function);
            }
        }
        Set<PyFunction> overridden = new HashSet<>();
        for (PyClass pyClass : classes) {
            PyClassInheritorsSearch.search(pyClass, true).forEach(inheritor -> {
                for (Iterator<PyFunction> it = candidates.get(pyClass).iterator(); it.hasNext(); ) {
                    PyFunction func = it.next();
                    if (inheritor.findMethodByName(func.getName(), false, null) != null) {
                        overridden.add(func);
                        it.remove();
                    }
                }
                return !candidates.isEmpty();
            });
            if (candidates.isEmpty()) {
                break;
            }
        }
        for (PyFunction func : overridden) {
            PsiElement identifier = func.getNameIdentifier();
            if (identifier != null) {
                result.add(new LineMarkerInfo<>(
                    identifier,
                    identifier.getTextRange(),
                    PlatformIconGroup.gutterOverridenmethod(),
                    Pass.LINE_MARKERS,
                    ourOverridingMethodTooltipProvider,
                    ourOverridingMethodNavigator,
                    GutterIconRenderer.Alignment.RIGHT
                ));
            }
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
