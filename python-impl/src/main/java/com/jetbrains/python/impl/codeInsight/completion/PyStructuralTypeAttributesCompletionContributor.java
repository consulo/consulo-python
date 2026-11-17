// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * This completion contributor tries to map structural type (if any) of qualifier under caret to set of concrete
 * classes (nominal types) that have (declare and/or inherit) all necessary attributes and thus complete other
 * attributes that may be accessed on this expression.
 * <p/>
 * Because it's somewhat computationally heavy operation that requires extensive resolution of superclasses and their
 * attributes, this contributor is activated only on smart completion.
 *
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class PyStructuralTypeAttributesCompletionContributor extends CompletionContributor implements DumbAware {

    private static final Logger LOG = Logger.getInstance(PyStructuralTypeAttributesCompletionContributor.class);

    private static final Set<String> COMMON_OBJECT_ATTRIBUTES = ImmutableSet.of(
        "__init__",
        "__new__",
        PyNames.DUNDER_STR,
        PyNames.DUNDER_REPR, // TODO __module__ actually belongs to object's metaclass, it's not available to instances
        "__doc__",
        "__class__",
        "__module__",
        "__dict__"
    );
    private static final PsiElementPattern.Capture<PsiElement> ATTRIBUTE_PATTERN =
        psiElement(PyTokenTypes.IDENTIFIER).afterLeaf(psiElement(PyTokenTypes.DOT)).withParent(psiElement(PyReferenceExpression.class));

    public PyStructuralTypeAttributesCompletionContributor() {
        extend(CompletionType.SMART, ATTRIBUTE_PATTERN, new AttributesCompletionProvider());
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static class AttributesCompletionProvider implements CompletionProvider {
        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters,
                                   ProcessingContext context,
                                   CompletionResultSet result) {

            PsiElement position = parameters.getPosition();
            PyReferenceExpression refExpr = as(position.getParent(), PyReferenceExpression.class);
            if (refExpr == null || !refExpr.isQualified() || parameters.getCompletionType() != CompletionType.SMART) {
                return;
            }

            TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(refExpr.getProject(), parameters.getOriginalFile());
            //noinspection ConstantConditions
            PyStructuralType structType = as(typeEvalContext.getType(refExpr.getQualifier()), PyStructuralType.class);
            LOG.debug("Structural type: " + structType);
            if (structType != null) {
                Set<String> names = Sets.newHashSet(structType.getAttributeNames());
                // Remove "dummy" identifier from the set of attributes
                names.remove(refExpr.getReferencedName());
                Set<PyClass> suitableClasses = suggestClassesFromUsedAttributes(refExpr, names, typeEvalContext);
                LOG.debug("Result classes that contain attributes " + names + ": ", suitableClasses);
                for (PyClass pyClass : suitableClasses) {
                    PsiElement origPosition = parameters.getOriginalPosition();
                    String prefix;
                    if (origPosition != null && origPosition.getNode().getElementType() == PyTokenTypes.IDENTIFIER) {
                        prefix = origPosition.getText();
                    }
                    else {
                        prefix = "";
                    }
                    Object[] variants = new PyClassTypeImpl(pyClass, false).getCompletionVariants(prefix, position, context);
                    for (Object variant : variants) {
                        if (variant instanceof LookupElement) {
                            result.addElement((LookupElement) variant);
                        }
                    }
                }
            }
        }

        private static Set<PyClass> suggestClassesFromUsedAttributes(PsiElement anchor,
                                                                     Set<String> seenAttrs,
                                                                     TypeEvalContext context) {
            Set<PyClass> candidates = new HashSet<>();
            Map<PyClass, Set<PyClass>> ancestorsCache = Maps.newHashMap();
            for (String attribute : seenAttrs) {
                // Search for some of these attributes like __init__ may produce thousands of candidates in average SDK
                if (COMMON_OBJECT_ATTRIBUTES.contains(attribute)) {
                    candidates.add(PyBuiltinCache.getInstance(anchor).getClass(PyNames.OBJECT));
                }
                else {
                    Collection<PyClass> declaringClasses = PyClassAttributesIndex.find(attribute, anchor.getProject());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Classes containing " + attribute + ": " +
                            ContainerUtil.map(declaringClasses, AttributesCompletionProvider::debugClassCoordinates));
                    }
                    candidates.addAll(declaringClasses);
                }
            }

            Set<PyClass> suitableClasses = new HashSet<>();
            for (PyClass candidate : candidates) {
                Set<String> inherited = getAllInheritedAttributeNames(candidate, context, ancestorsCache);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("All attributes of " + debugClassCoordinates(candidate) + ": " + inherited);
                }
                if (inherited.containsAll(seenAttrs)) {
                    suitableClasses.add(candidate);
                }
            }
            return Collections.unmodifiableSet(suitableClasses);
        }

        private static Set<String> getAllInheritedAttributeNames(PyClass candidate,
                                                                 TypeEvalContext context,
                                                                 Map<PyClass, Set<PyClass>> ancestorsCache) {
            Set<String> availableAttrs = Sets.newHashSet(PyClassAttributesIndex.getAllDeclaredAttributeNames(candidate));
            for (PyClass parent : getAncestorClassesFast(candidate, context, ancestorsCache)) {
                availableAttrs.addAll(PyClassAttributesIndex.getAllDeclaredAttributeNames(parent));
            }
            return availableAttrs;
        }

        /**
         * A simpler and faster alternative to {@link PyClass#getAncestorClasses(TypeEvalContext)}.
         * The approach used here does not require proper MRO order of ancestors and its performance is greatly improved by reusing
         * intermediate results in case of a large class hierarchy.
         */
        private static Set<PyClass> getAncestorClassesFast(PyClass pyClass,
                                                           TypeEvalContext context,
                                                           Map<PyClass, Set<PyClass>> ancestorsCache) {
            Set<PyClass> ancestors = ancestorsCache.get(pyClass);
            if (ancestors != null) {
                return ancestors;
            }
            // Sentinel value to prevent infinite recursion
            ancestorsCache.put(pyClass, Collections.emptySet());
            Set<PyClass> result = new HashSet<>();
            try {
                for (PyClassLikeType baseType : pyClass.getSuperClassTypes(context)) {
                    if (!(baseType instanceof PyClassType)) {
                        continue;
                    }
                    PyClass baseClass = ((PyClassType) baseType).getPyClass();
                    result.add(baseClass);
                    result.addAll(getAncestorClassesFast(baseClass, context, ancestorsCache));
                }
            }
            finally {
                // May happen in case of cyclic inheritance
                result.remove(pyClass);
                ancestorsCache.put(pyClass, Collections.unmodifiableSet(result));
            }
            return result;
        }

        private static String debugClassCoordinates(PyClass cls) {
            return cls.getQualifiedName() + " (" + cls.getContainingFile().getVirtualFile().getPath() + ")";
        }
    }
}
