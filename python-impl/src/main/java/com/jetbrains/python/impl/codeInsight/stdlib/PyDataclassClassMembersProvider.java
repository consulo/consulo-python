// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.codeInsight.InitFieldsAccumulator;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Adds members for dataclass-like classes.
 */
@ExtensionImpl
public class PyDataclassClassMembersProvider extends PyClassMembersProviderBase {

    private enum ProvidedDataclassMember {
        DUNDER_SLOTS(PyDataclassNames.Dataclasses.DUNDER_SLOTS),
        DUNDER_ATTRS(PyDataclassNames.Attrs.DUNDER_ATTRS),
        DUNDER_MATCH_ARGS(PyDataclassNames.Dataclasses.DUNDER_MATCH_ARGS),
        DUNDER_LT(PyNames.LT),
        DUNDER_LE(PyNames.LE),
        DUNDER_GT(PyNames.GT),
        DUNDER_GE(PyNames.GE);

        private static final Map<String, ProvidedDataclassMember> byName = new HashMap<>();

        static {
            for (ProvidedDataclassMember member : values()) {
                byName.put(member.memberName, member);
            }
        }

        private final String memberName;
        private final Key<CachedValue<@Nullable PyCustomMember>> key;

        ProvidedDataclassMember(String memberName) {
            this.memberName = memberName;
            this.key = Key.create("py.dataclass.member." + memberName);
        }

        static @Nullable ProvidedDataclassMember fromName(String name) {
            return byName.get(name);
        }
    }

    @Override
    public @Nullable PsiElement resolveMember(PyClassType type,
                                              String name,
                                              @Nullable PsiElement location,
                                              PyResolveContext resolveContext) {
        PyClass pyClass = type.getPyClass();
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        ProvidedDataclassMember member = ProvidedDataclassMember.fromName(name);
        if (member == null) {
            return null;
        }
        PyCustomMember customMember = getCachedCustomMember(type, member, context);
        return customMember != null ? customMember.resolve(pyClass, resolveContext) : null;
    }

    @Override
    public Collection<PyCustomMember> getMembers(PyClassType clazz, @Nullable PsiElement location, TypeEvalContext context) {
        List<PyCustomMember> result = new ArrayList<>();
        for (ProvidedDataclassMember member : ProvidedDataclassMember.values()) {
            PyCustomMember customMember = getCachedCustomMember(clazz, member, context);
            if (customMember != null) {
                result.add(customMember);
            }
        }
        return result;
    }

    private static @Nullable PyCustomMember getCachedCustomMember(PyClassType type,
                                                                  ProvidedDataclassMember member,
                                                                  TypeEvalContext context) {
        return LanguageCachedValueUtil.getCachedValue(type.getPyClass(), member.key, () -> {
            PyCustomMember customMember = getCustomMember(type, member, context);
            return CachedValueProvider.Result.create(customMember, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private static @Nullable PyCustomMember getCustomMember(PyClassType type,
                                                            ProvidedDataclassMember member,
                                                            TypeEvalContext context) {
        PyClass pyClass = type.getPyClass();
        PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(pyClass, context);
        switch (member) {

            // Member __slots__ caused by dataclass.slots
            case DUNDER_SLOTS: {
                if (dataclassParameters == null || !dataclassParameters.getSlots()) {
                    return null;
                }

                PyClassType strType = PyBuiltinCache.getInstance(pyClass).getStrType();
                if (strType == null) {
                    return null;
                }
                // creating the following PyTupleType requires caching the result to avoid Idempotency Errors at runtime
                PyTupleType tupleOfStrings = PyTupleType.createHomogeneous(pyClass, strType);
                if (tupleOfStrings == null) {
                    return null;
                }
                String qNameTuple = tupleOfStrings.getPyClass().getQualifiedName();
                return new PyCustomMember(member.memberName, qNameTuple, it -> tupleOfStrings);
            }

            // Member __attrs_attrs__ caused by decorator @attrs.define
            case DUNDER_ATTRS: {
                if (dataclassParameters == null || dataclassParameters.getType() != PyAttrsDataclassType.INSTANCE) {
                    return null;
                }

                PyClass objectClass = PyBuiltinCache.getInstance(pyClass).getClass(PyNames.OBJECT);
                return new PyCustomMember(member.memberName, objectClass).asClassVar();
            }

            // Member __match_args__ caused by @dataclass(match_args=True)
            case DUNDER_MATCH_ARGS: {
                if (dataclassParameters == null || !dataclassParameters.getMatchArgs()) {
                    return null;
                }
                if (pyClass.getOwnMatchArgs() != null) {
                    return null;
                }

                List<String> matchArgs = constructGeneratedMatchArgs(type, context);
                if (matchArgs == null) {
                    return null;
                }
                PyTupleType tupleOfStrings = PyTypeUtil.createTupleOfLiteralStringsType(pyClass, matchArgs);
                if (tupleOfStrings == null) {
                    return null;
                }
                String qNameTuple = tupleOfStrings.getPyClass().getQualifiedName();
                return new PyCustomMember(member.memberName, qNameTuple, it -> tupleOfStrings).toPsiElement(pyClass).asClassVar();
            }

            // Members __lt__, __le__, __gt__, __ge__ caused by @dataclass(order=True)
            case DUNDER_LT:
            case DUNDER_LE:
            case DUNDER_GT:
            case DUNDER_GE: {
                if (dataclassParameters == null || !dataclassParameters.getOrder()) {
                    return null;
                }
                if (pyClass.findMethodByName(member.memberName, false, context) != null) {
                    return null;
                }

                PyClassType boolType = PyBuiltinCache.getInstance(pyClass).getBoolType();
                if (boolType == null) {
                    return null;
                }
                PyClassType instanceType = type.toInstance();
                return new PyCustomMember(member.memberName, (String) null, it ->
                    new PyCallableTypeImpl(
                        List.of(
                            PyCallableParameterImpl.selfNonPsi(instanceType),
                            PyCallableParameterImpl.nonPsi("other", instanceType)
                        ),
                        boolType
                    )
                ).toPsiElement(pyClass);
            }
        }
        return null;
    }

    /**
     * The names generated into `__match_args__` for {@code classType} when it is a dataclass declared with `match_args=True`, or
     * {@code null} when no `__match_args__` is generated. Runs the shared MRO walk
     * ({@link PyDataclassResolverKt#collectDataclassInitFields}) and takes the
     * positional part of the controlling framework's init signature. This is model construction shared by the type provider
     * and the class-members provider, so it lives in the model layer rather than in `PyDataclassTypeProvider`.
     */
    private static @Nullable List<String> constructGeneratedMatchArgs(PyClassType classType, TypeEvalContext context) {
        PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(classType.getPyClass(), context);
        if (parameters == null || !parameters.getMatchArgs()) {
            return null;
        }

        InitFieldsAccumulator acc = PyDataclassResolverKt.collectDataclassInitFields(classType, context, false);
        if (acc == null) {
            return null;
        }
        PyDataclassParameters controlling = acc.getControllingParameters();
        if (controlling == null) {
            return null;
        }
        PyDataclassResolver resolver = controlling.getType().getResolver();
        if (resolver == null) {
            return null;
        }
        List<List<PyCallableParameter>> parameterSets = resolver.buildInitSignatureParameterSets(acc);
        if (parameterSets.isEmpty()) {
            return null;
        }
        List<PyCallableParameter> params = parameterSets.get(0);
        List<String> result = new ArrayList<>();
        for (PyCallableParameter it : params) {
            if (it.isKeywordOnlySeparator()) {
                break;
            }
            String name = it.getName();
            if (name != null) {
                result.add(name);
            }
        }
        return result;
    }
}
