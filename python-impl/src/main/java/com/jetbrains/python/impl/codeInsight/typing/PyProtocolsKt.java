// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Java counterpart of the top-level declarations in JB {@code PyProtocols.kt}.
 * <p>
 * {@code typealias ProtocolAndSubclassElements = Pair<PyTypeMember, List<PyTypeMember>>}
 */
public final class PyProtocolsKt {
    private static final String TYPED_DICT_FALLBACK = "_typeshed._type_checker_internals.TypedDictFallback";

    private static final Set<String> ignoredNamesForProtocolMatching =
        Set.of(PyNames.CLASS_GETITEM, PyNames.SLOTS, PyNames.NAME, PyNames.QUALNAME, PyNames.MODULE, PyNames.ANNOTATIONS);

    private PyProtocolsKt() {
    }

    public static boolean isProtocol(PyClassLikeType type, TypeEvalContext context) {
        return containsProtocol(type.getSuperClassTypes(context));
    }

    public static boolean isProtocol(PyClass cls, TypeEvalContext context) {
        return containsProtocol(cls.getSuperClassTypes(context));
    }

    public static boolean isRuntimeCheckable(PyClassType type, TypeEvalContext context) {
        return PyKnownDecoratorUtil.knownDecoratorsOf(type.getPyClass(), context)
            .containsAny(PyKnownDecorator.TYPING_RUNTIME_CHECKABLE, PyKnownDecorator.TYPING_RUNTIME_CHECKABLE_EXT,
                PyKnownDecorator.TYPING_RUNTIME, PyKnownDecorator.TYPING_RUNTIME_EXT);
    }

    public static boolean matchingProtocolDefinitions(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context) {
        return expected instanceof PyClassLikeType expectedType &&
            actual instanceof PyClassLikeType actualType &&
            expectedType.isDefinition() &&
            actualType.isDefinition() &&
            isProtocol(expectedType, context) &&
            isProtocol(actualType, context);
    }

    public static List<Pair<PyTypeMember, List<PyTypeMember>>> inspectProtocolSubclass(PyClassType protocol,
                                                                                         PyClassType subclass,
                                                                                         TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<Pair<PyTypeMember, List<PyTypeMember>>> result = new ArrayList<>();

        List<PyTypeMember> protocolMembers = getProtocolMembers(protocol, context);

        for (PyTypeMember protocolMember : protocolMembers) {
            PsiElement protocolElement = protocolMember.getElement();
            if (protocolElement == null) {
                continue;
            }
            PyFunction contextFunction = PsiTreeUtil.getContextOfType(protocolElement, PyFunction.class, true);
            if (contextFunction != null && Objects.equals(contextFunction.getContainingClass(), protocol.getPyClass())) {
                continue;
            }

            String name = protocolMember.getName();
            if (name == null) {
                continue;
            }
            if (PyNames.CALL.equals(name)) {
                // A class object matches a callable protocol through its constructor.
                // When the protocol type is a class object, match `__call__` against the `__call__` of the subclass instance.
                PyClassType callee = protocol.isDefinition() ? subclass.toInstance() : subclass;
                List<PyTypeMember> invokedMethods;
                if (callee.isDefinition()) {
                    invokedMethods = callee.findMember(PyNames.INIT, resolveContext);
                    if (invokedMethods.isEmpty()) {
                        invokedMethods = callee.findMember(PyNames.NEW, resolveContext);
                    }
                }
                else {
                    invokedMethods = callee.findMember(PyNames.CALL, resolveContext);
                }
                if (!invokedMethods.isEmpty()) {
                    result.add(Pair.create(protocolMember, invokedMethods));
                }
                else {
                    PyType callableType = PyCallExpressionHelper.createCallableFromClass(callee, resolveContext);
                    List<PyTypeMember> fallbackTypes = new ArrayList<>();
                    for (PyType it : PyTypeUtil.getCallableItems(callableType)) {
                        fallbackTypes.add(new PyTypeMember((PsiElement) null, it)); // TODO null
                    }
                    result.add(Pair.create(protocolMember, fallbackTypes));
                }
            }
            else {
                result.add(Pair.create(protocolMember, findProtocolMember(subclass, name, context)));
            }
        }

        return result;
    }

    private static List<PyTypeMember> findProtocolMember(PyClassType type, String name, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        if (type.isDefinition()) {
            PyClassLikeType metaClassType = type.getMetaClassType(context, true);
            if (metaClassType != null) {
                List<PyTypeMember> memberInMetaClass = metaClassType.findMember(name, resolveContext);
                if (!memberInMetaClass.isEmpty()) {
                    return memberInMetaClass;
                }
            }
        }
        List<PyTypeMember> members = type.findMember(name, resolveContext);
        if (members.isEmpty() && type instanceof PyTypedDictType) {
            // A TypedDict type is based on `dict`, but members such as `__required_keys__` belong to the TypedDict fallback class of typeshed
            PyClass pyClass = type.getPyClass();
            PyClass fallbackClass = PyPsiFacade.getInstance(pyClass.getProject()).createClassByQName(TYPED_DICT_FALLBACK, pyClass);
            if (fallbackClass == null) {
                return members;
            }
            return new PyClassTypeImpl(fallbackClass, type.isDefinition()).findMember(name, resolveContext);
        }
        return members;
    }

    public static List<PyTypeMember> getProtocolMembers(PyClassLikeType type, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<PyTypeMember> result = new ArrayList<>();

        List<PyTypeMember> superClassesMembers = new ArrayList<>();
        for (PyClassLikeType it : type.toInstance().getSuperClassTypes(context)) {
            if (it != null && isProtocol(it, context)) {
                superClassesMembers.addAll(it.toInstance().getAllMembers(resolveContext));
            }
        }
        List<PyTypeMember> protocolMembers = new ArrayList<>(type.toInstance().getAllMembers(resolveContext));
        protocolMembers.addAll(superClassesMembers);


        for (PyTypeMember protocolMember : protocolMembers) {
            PsiElement protocolElement = protocolMember.getElement();
            if (protocolElement == null) {
                continue;
            }
            if (protocolElement instanceof PyPossibleClassMember possibleClassMember) {
                PyClass cls = possibleClassMember.getContainingClass();
                if (cls != null && !isProtocol(cls, context)) {
                    continue;
                }
            }
            if (protocolElement instanceof PyTypeParameter) {
                continue;
            }

            String name = protocolMember.getName();
            if (name == null) {
                continue;
            }
            if (ignoredNamesForProtocolMatching.contains(name)) {
                continue;
            }
            result.add(protocolMember);
        }

        return result;
    }

    private static boolean containsProtocol(List<? extends @Nullable PyClassLikeType> types) {
        for (PyClassLikeType type : types) {
            String classQName = type != null ? type.getClassQName() : null;
            if (PyTypingTypeProvider.PROTOCOL.equals(classQName) || PyTypingTypeProvider.PROTOCOL_EXT.equals(classQName)) {
                return true;
            }
        }
        return false;
    }
}
