/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.CollectionQuery;
import consulo.application.util.query.Query;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.collection.MultiMap;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @author yole
 */
@ExtensionImpl
public class PyLineMarkerProvider implements LineMarkerProvider, PyLineSeparatorUtil.Provider {

    @Nonnull
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static class TooltipProvider implements Function<PsiElement, String> {
        private final String myText;

        private TooltipProvider(String text) {
            myText = text;
        }

        @Override
        public String apply(PsiElement psiElement) {
            return myText;
        }
    }

    private static final Function<PyClass, String> ourSubclassTooltipProvider = pyClass -> {
        StringBuilder builder = new StringBuilder("<html>Is subclassed by:");
        AtomicInteger count = new AtomicInteger();
        PyClassInheritorsSearch.search(pyClass, true).forEach(pyClass1 -> {
            if (count.incrementAndGet() >= 10) {
                builder.setLength(0);
                builder.append("Has subclasses");
                return false;
            }
            builder.append("<br>&nbsp;&nbsp;").append(pyClass1.getName());
            return true;
        });
        return builder.toString();
    };

    private static final Function<PyFunction, String> ourOverridingMethodTooltipProvider = pyFunction -> {
        StringBuilder builder = new StringBuilder("<html>Is overridden in:");
        AtomicInteger count = new AtomicInteger();
        PyClassInheritorsSearch.search(pyFunction.getContainingClass(), true).forEach(pyClass -> {
            if (count.incrementAndGet() >= 10) {
                builder.setLength(0);
                builder.append("Has overridden methods");
                return false;
            }
            if (pyClass.findMethodByName(pyFunction.getName(), false, null) != null) {
                builder.append("<br>&nbsp;&nbsp;").append(pyClass.getName());
            }
            return true;
        });
        return builder.toString();
    };

    private static final PyLineMarkerNavigator<PsiElement> ourSuperMethodNavigator = new PyLineMarkerNavigator<>() {
        @Override
        @RequiredReadAction
        protected String getTitle(PsiElement elt) {
            return "Choose Super Method of " + ((PyFunction) elt.getParent()).getName();
        }

        @Nullable
        @Override
        protected Query<PsiElement> search(PsiElement elt, @Nonnull TypeEvalContext context) {
            return elt.getParent() instanceof PyFunction function ? PySuperMethodsSearch.search(function, context) : null;
        }
    };

    private static final PyLineMarkerNavigator<PsiElement> ourSuperAttributeNavigator = new PyLineMarkerNavigator<>() {
        @Override
        protected String getTitle(PsiElement elt) {
            return "Choose Super Attribute of " + ((PyTargetExpression) elt).getName();
        }

        @Nonnull
        @Override
        @RequiredReadAction
        protected Query<PsiElement> search(PsiElement elt, @Nonnull TypeEvalContext context) {
            List<PsiElement> result = new ArrayList<>();
            PyClass containingClass = PsiTreeUtil.getParentOfType(elt, PyClass.class);
            if (containingClass != null && elt instanceof PyTargetExpression targetExpr) {
                for (PyClass ancestor : containingClass.getAncestorClasses(context)) {
                    PyTargetExpression attribute = ancestor.findClassAttribute(targetExpr.getReferencedName(), false, context);
                    if (attribute != null) {
                        result.add(attribute);
                    }
                }
            }
            return new CollectionQuery<>(result);
        }
    };

    private static final PyLineMarkerNavigator<PyClass> ourSubclassNavigator = new PyLineMarkerNavigator<>() {
        @Override
        @RequiredReadAction
        protected String getTitle(PyClass elt) {
            return "Choose Subclass of " + elt.getName();
        }

        @Override
        protected Query<PyClass> search(PyClass elt, @Nonnull TypeEvalContext context) {
            return PyClassInheritorsSearch.search(elt, true);
        }
    };

    private static final PyLineMarkerNavigator<PyFunction> ourOverridingMethodNavigator = new PyLineMarkerNavigator<>() {
        @Override
        @RequiredReadAction
        protected String getTitle(PyFunction elt) {
            return "Choose Overriding Method of " + elt.getName();
        }

        @Override
        protected Query<PyFunction> search(PyFunction elt, @Nonnull TypeEvalContext context) {
            return PyOverridingMethodsSearch.search(elt, true);
        }
    };

    @Override
    @RequiredReadAction
    public LineMarkerInfo getLineMarkerInfo(@Nonnull PsiElement element) {
        ASTNode node = element.getNode();
        if (node != null && node.getElementType() == PyTokenTypes.IDENTIFIER && element.getParent() instanceof PyFunction function) {
            return getMethodMarker(element, function);
        }
        if (element instanceof PyTargetExpression targetExpr && PyUtil.isClassAttribute(element)) {
            return getAttributeMarker(targetExpr);
        }
        if (DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS && isSeparatorAllowed(element)) {
            return PyLineSeparatorUtil.addLineSeparatorIfNeeded(this, element);
        }
        return null;
    }

    @Override
    public boolean isSeparatorAllowed(PsiElement element) {
        return element instanceof PyFunction || element instanceof PyClass;
    }

    @Nullable
    @RequiredReadAction
    private static LineMarkerInfo<PsiElement> getMethodMarker(PsiElement element, PyFunction function) {
        if (PyNames.INIT.equals(function.getName())) {
            return null;
        }
        TypeEvalContext context = TypeEvalContext.codeAnalysis(element.getProject(), function.getContainingFile());
        PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
        if (superMethod != null) {
            PyClass superClass = null;
            if (superMethod instanceof PyFunction superFunction) {
                superClass = superFunction.getContainingClass();
            }
            // TODO: show "implementing" instead of "overriding" icon for Python implementations of Java interface methods
            return new LineMarkerInfo<>(
                element,
                element.getTextRange().getStartOffset(),
                PlatformIconGroup.gutterOverridingmethod(),
                Pass.LINE_MARKERS,
                superClass == null ? null : new TooltipProvider("Overrides method in " + superClass.getName()),
                ourSuperMethodNavigator
            );
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static LineMarkerInfo<PsiElement> getAttributeMarker(PyTargetExpression element) {
        String name = element.getReferencedName();
        if (name == null) {
            return null;
        }
        PyClass containingClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        if (containingClass == null) {
            return null;
        }
      List<PyClass> ancestors =
          containingClass.getAncestorClasses(TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()));
      for (PyClass ancestor : ancestors) {
            PyTargetExpression ancestorAttr = ancestor.findClassAttribute(name, false, null);
            if (ancestorAttr != null) {
                return new LineMarkerInfo<>(
                    element,
                    element.getTextRange().getStartOffset(),
                    PlatformIconGroup.gutterOverridingmethod(),
                    Pass.LINE_MARKERS,
                    new TooltipProvider("Overrides attribute in " + ancestor.getName()),
                    ourSuperAttributeNavigator
                );
            }
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public void collectSlowLineMarkers(@Nonnull List<PsiElement> elements, @Nonnull Collection<LineMarkerInfo> result) {
        Set<PyFunction> functions = new HashSet<>();
        for (PsiElement element : elements) {
            if (element instanceof PyClass pyClass) {
                collectInheritingClasses(pyClass, result);
            }
            else if (element instanceof PyFunction function) {
                functions.add(function);
            }
        }
        collectOverridingMethods(functions, result);
    }

    private static void collectInheritingClasses(PyClass element, Collection<LineMarkerInfo> result) {
        if (PyClassInheritorsSearch.search(element, false).findFirst() != null) {
            result.add(new LineMarkerInfo<>(
                element,
                element.getTextOffset(),
                PlatformIconGroup.gutterOverridenmethod(),
                Pass.LINE_MARKERS,
                ourSubclassTooltipProvider,
                ourSubclassNavigator
            ));
        }
    }

    @RequiredReadAction
    private static void collectOverridingMethods(Set<PyFunction> functions, Collection<LineMarkerInfo> result) {
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
            result.add(new LineMarkerInfo<>(
                func,
                func.getTextOffset(),
                PlatformIconGroup.gutterOverridenmethod(),
                Pass.LINE_MARKERS,
                ourOverridingMethodTooltipProvider,
                ourOverridingMethodNavigator
            ));
        }
    }
}
