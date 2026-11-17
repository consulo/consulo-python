// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverridingClassMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class PyOverridingClassDunderMembersProvider extends PyClassMembersProviderBase implements PyOverridingClassMembersProvider {

    @Override
    public @Nullable PsiElement resolveMember(PyClassType type,
                                              String name,
                                              @Nullable PsiElement location,
                                              PyResolveContext resolveContext) {
        if (PyUtil.isObjectClass(type.getPyClass())) {
            return null;
        }

        PyExpression pyLocation = location instanceof PyExpression expression ? expression : null;
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        AccessDirection direction =
            pyLocation != null && context.maySwitchToAST(location) ? AccessDirection.of(pyLocation) : AccessDirection.READ;

        List<PsiElement> resolved = switch (name) {
            case PyNames.__CLASS__ -> processDunderClass(type, pyLocation, direction, context);
            case PyNames.DOC ->
                processNotOverridden(type, PyNames.DOC, pyLocation, direction, context, overridesDocOrModule(type, location).getFirst());
            case "__module__" ->
                processNotOverridden(type, "__module__", pyLocation, direction, context, overridesDocOrModule(type, location).getSecond());
            default -> Collections.emptyList();
        };
        return resolved.isEmpty() ? null : resolved.get(0);
    }

    @Override
    public Collection<PyCustomMember> getMembers(PyClassType clazz, @Nullable PsiElement location, TypeEvalContext context) {
        if (PyUtil.isObjectClass(clazz.getPyClass())) {
            return Collections.emptyList();
        }

        PyExpression pyLocation = location instanceof PyExpression expression ? expression : null;
        AccessDirection direction =
            pyLocation != null && context.maySwitchToAST(location) ? AccessDirection.of(pyLocation) : AccessDirection.READ;

        List<PyCustomMember> result = new ArrayList<>();

        result.addAll(toCustomMembers(PyNames.__CLASS__, processDunderClass(clazz, pyLocation, direction, context)));

        Pair<Boolean, Boolean> overrides = overridesDocOrModule(clazz, location);
        boolean overridesDoc = overrides.getFirst();
        boolean overridesModule = overrides.getSecond();
        result.addAll(toCustomMembers(PyNames.DOC, processNotOverridden(clazz, PyNames.DOC, pyLocation, direction, context, overridesDoc)));
        result.addAll(toCustomMembers("__module__",
                                      processNotOverridden(clazz, "__module__", pyLocation, direction, context, overridesModule)));

        return result;
    }

    private static List<PsiElement> processDunderClass(
        PyClassType type,
        @Nullable PyExpression location,
        AccessDirection direction,
        TypeEvalContext context
    ) {
        if (type.isDefinition() && type.getPyClass().isNewStyleClass(context) || /* override */
            !type.isDefinition() && !type.getPyClass().isNewStyleClass(context) /* provide */) {
            return resolveInObject(type, PyNames.__CLASS__, location, direction, context);
        }
        else {
            return Collections.emptyList();
        }
    }

    private static List<PsiElement> processNotOverridden(
        PyClassType type,
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        TypeEvalContext context,
        boolean overridden
    ) {
        return !overridden ? resolveInObject(type, name, location, direction, context) : Collections.emptyList();
    }

    private static List<PyCustomMember> toCustomMembers(String name, List<PsiElement> resolved) {
        List<PyCustomMember> result = new ArrayList<>(resolved.size());
        for (PsiElement it : resolved) {
            result.add(new PyCustomMember(name, it));
        }
        return result;
    }

    private static List<PsiElement> resolveInObject(
        PyClassType type,
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        TypeEvalContext context
    ) {
        PyClassType objectType = PyBuiltinCache.getInstance(type.getPyClass()).getObjectType();
        if (objectType == null) {
            return Collections.emptyList();
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<? extends RatedResolveResult> results = objectType.resolveMember(name, location, direction, resolveContext);
        if (results == null) {
            return Collections.emptyList();
        }
        List<PsiElement> elements = new ArrayList<>();
        for (RatedResolveResult it : results) {
            PsiElement element = it.getElement();
            if (element != null) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static Pair<Boolean, Boolean> overridesDocOrModule(PyClassType type, @Nullable PsiElement location) {
        var cls = type.getPyClass();

        boolean[] overridesDoc = {false};
        boolean[] overridesModule = {false};

        PsiScopeProcessor processor = new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (element instanceof PsiNamedElement namedElement) {
                    String elementName = namedElement.getName();
                    if (PyNames.DOC.equals(elementName)) {
                        overridesDoc[0] = true;
                    }
                    else if ("__module__".equals(elementName)) {
                        overridesModule[0] = true;
                    }
                }

                return !overridesDoc[0] || !overridesModule[0];
            }
        };

        cls.processClassLevelDeclarations(processor);
        if (!type.isDefinition()) {
            cls.processInstanceLevelDeclarations(processor, location);
        }

        return Pair.create(overridesDoc[0], overridesModule[0]);
    }
}
