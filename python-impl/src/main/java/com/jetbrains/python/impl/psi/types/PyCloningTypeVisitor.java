// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.Sets;
import consulo.util.lang.ref.Ref;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Allows to re-construct a {@link PyType} instance replacing some of its components.
 * <p>
 * Should be used as follows:
 * <pre>{@code
 * PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(typeEvalContext) {
 *   @Override
 *   public PyType visitPyClassType(PyClassType classType) {
 *     if ("foo.Bar".equals(classType.getPyClass().getQualifiedName())) {
 *       PyClassType replacement = ... // Create a replacement type
 *       return replacement;
 *     }
 *     return clone(classType);
 *   }
 * });
 * }</pre>
 * <p>
 * If you need to recursively invoke cloning for nested types use {@link #clone(PyType)} instead of raw
 * {@code PyTypeVisitor.visitPyType(type, this)} or {@code type.acceptTypeVisitor(this)}.
 * This method has protection against recursive types and properly handles "unknown" nullable {@link PyType} instances.
 */
public abstract class PyCloningTypeVisitor extends PyTypeVisitorExt<PyType> {
    private final TypeEvalContext myTypeEvalContext;
    // Synchronized rather than concurrent: while `python.type.any` is off, an unknown type is a null, and a null key is what a
    // ConcurrentHashMap refuses. A lazily cloned component is asked for on any thread, outside the traversal that filled these.
    private final Set<@Nullable PyType> cloning = Collections.synchronizedSet(Sets.newIdentityHashSet());
    private final Map<@Nullable PyType, @Nullable PyType> cloned = Collections.synchronizedMap(new IdentityHashMap<>());

    public static @Nullable PyType clone(@Nullable PyType type, PyCloningTypeVisitor visitor) {
        return visitor.clone(type);
    }

    protected PyCloningTypeVisitor(TypeEvalContext context) {
        myTypeEvalContext = context;
    }

    // Intentionally not marked as @Nullable to avoid false positives. 
    // A recursive type is an exceptional case.
    protected final <T extends PyType> T clone(@Nullable PyType type) {
        //noinspection unchecked
        return (T)doClone(type);
    }

    private @Nullable PyType doClone(@Nullable PyType type) {
        PyAnyType.validate(type);
        if (cloned.containsKey(type)) {
            return cloned.get(type);
        }
        if (!cloning.add(type)) {
            // The "unknown" breaking the cycle is valid for this path only, so it is deliberately not memoized: a type whose
            // components are cloned lazily asks for the same type again outside of any cycle, and has to get its real clone.
            return PyAnyType.getUnknown();
        }
        try {
            PyType result = visit(type, this);
            PyAnyType.validate(result);
            cloned.put(type, result);
            return result;
        }
        finally {
            cloning.remove(type);
        }
    }

    @Override
    public PyType visitPyLiteralType(PyLiteralType literalType) {
        return literalType;
    }

    @Override
    public PyType visitPyLiteralStringType(PyLiteralStringType literalStringType) {
        return literalStringType;
    }

    @Override
    public PyType visitPyModuleType(PyModuleType moduleType) {
        return moduleType;
    }

    @Override
    public PyType visitPyParamSpecType(PyParamSpecType paramSpecType) {
        return paramSpecType;
    }

    @Override
    public PyType visitPyTupleType(PyTupleType tupleType) {
        return new PyTupleType(
            tupleType.getPyClass(),
            ContainerUtil.map(tupleType.getElementTypes(), type -> clone(type)),
            tupleType.isHomogeneous(),
            tupleType.isDefinition()
        );
    }

    @Override
    public PyType visitPyNamedTupleType(PyNamedTupleType namedTupleType) {
        return namedTupleType;
    }

    @Override
    public PyType visitPySelfType(PySelfType selfType) {
        return selfType;
    }

    @Override
    public PyType visitPyTypedDictType(PyTypedDictType typedDictType) {
        // Cloned lazily, so that cloning a TypedDict does not force item types that may refer back to it.
        return new PyTypedDictType(
            typedDictType.getName(),
            evalContext -> cloneFields(typedDictType, evalContext),
            typedDictType.myClass,
            typedDictType.isDefinition(),
            typedDictType.getDeclarationElement(),
            typedDictType.isClosed(),
            evalContext -> cloneExtraItems(typedDictType, evalContext),
            typedDictType.getDeclaredTypeParameters(),
            ContainerUtil.map(typedDictType.getTypeArguments(), type -> clone(type)));
    }

    private PyTypedDictType.FieldTypeAndTotality cloneExtraItems(PyTypedDictType typedDictType,
                                                                         TypeEvalContext context) {
        PyTypedDictType.FieldTypeAndTotality extraItems = typedDictType.extraItems(context);
        return new PyTypedDictType.FieldTypeAndTotality(extraItems.getValue(), clone(extraItems.getType()), extraItems.getQualifiers());
    }

    private Map<String, PyTypedDictType.FieldTypeAndTotality> cloneFields(PyTypedDictType typedDictType,
                                                                                  TypeEvalContext context) {
        // TODO Copied from PyTypeChecker.substitute, revise
        return typedDictType.fields(context).entrySet().stream().collect(
            Collectors.toMap(
                Map.Entry::getKey,
                field -> new PyTypedDictType.FieldTypeAndTotality(
                    field.getValue().getValue(),
                    clone(field.getValue().getType()),
                    field.getValue().getQualifiers()
                )
            )
        );
    }

    @Override
    public PyType visitPyUnionType(PyUnionType unionType) {
        return PyUnionType.unionOrUnknown(ContainerUtil.map(unionType.getMembers(), type -> clone(type)));
    }

    @Override
    public PyType visitPyUnsafeUnionType(PyUnsafeUnionType unsafeUnionType) {
        return PyUnsafeUnionType.unsafeUnion(ContainerUtil.map(unsafeUnionType.getMembers(), type -> clone(type)));
    }

    @Override
    public PyType visitPyIntersectionType(PyIntersectionType intersectionType) {
        return PyIntersectionType.intersectionOrTop(ContainerUtil.map(intersectionType.getMembers(), type -> clone(type)));
    }

    @Override
    public PyType visitPyTypingNewType(PyTypingNewType typingNewType) {
        return typingNewType;
    }

    @Override
    public PyType visitPyNarrowedType(PyNarrowedType narrowedType) {
        return narrowedType.substitute(clone(narrowedType.getNarrowedType()));
    }

    @Override
    public PyType visitPyTypeFormType(PyTypeFormType typeFormType) {
        return typeFormType.substitute(clone(typeFormType.getRepresentedType()));
    }

    @Override
    public PyType visitPyConcatenateType(PyConcatenateType concatenateType) {
        return new PyConcatenateType(
            ContainerUtil.map(concatenateType.getFirstTypes(), this::clone),
            clone(concatenateType.getParamSpec())
        );
    }

    @Override
    public PyType visitPyType(PyType type) {
        return type;
    }

    @Override
    public PyType visitPyClassType(PyClassType classType) {
        if (!classType.isParameterized()) {
            return classType;
        }
        if (classType instanceof PyClassTypeImpl impl) {
            return impl.withUserDataCopy(
                impl.createInstance(
                    classType.getPyClass(),
                    classType.isDefinition(),
                    ContainerUtil.map(classType.getTypeArguments(), type -> clone(type))
                )
            );
        }
        return new PyCollectionTypeImpl(
            classType.getPyClass(),
            classType.isDefinition(),
            ContainerUtil.map(classType.getTypeArguments(), type -> clone(type))
        );
    }

    @Override
    public PyType visitPyClassLikeType(PyClassLikeType classLikeType) {
        return classLikeType;
    }

    @Override
    public PyType visitPyFunctionType(PyFunctionType functionType) {
        // Create a new callable type for a function type. The constructor of PyFunctionType doesn't accept its return type explicitly.
        return super.visitPyFunctionType(functionType);
    }

    @Override
    public PyType visitPyCallableType(PyCallableType callableType) {
        // Cloning a recursive type yields PyAnyType.unknown (see doClone), which is not a
        // PyCallableParameterVariadicType. Use a safe cast so that case degrades to null params instead of a
        // ClassCastException once `python.type.any` is enabled (it was null when disabled).
        var parametersType = callableType.getParametersType(myTypeEvalContext);
        PyType clonedParams = parametersType != null ? clone(parametersType) : null;
        PyCallableParameterVariadicType clonedParametersType =
            clonedParams instanceof PyCallableParameterVariadicType variadic ? variadic : null;
        var typeParameters = callableType.getTypeParameters(myTypeEvalContext);
        return new PyCallableTypeImpl(
            typeParameters != null ? ContainerUtil.map(typeParameters, this::clone) : null,
            clonedParametersType,
            clone(callableType.getReturnType(myTypeEvalContext)),
            callableType.getCallable(),
            callableType.getModifier()
        );
    }

    @Override
    public PyType visitPyOverloadType(PyOverloadType overloadType) {
        return new PyOverloadType(ContainerUtil.mapNotNull(overloadType.getItems(), this::clone), overloadType.getImpl());
    }

    @Override
    public PyType visitPyTypeVarType(PyTypeVarType typeVarType) {
        return typeVarType;
    }

    @Override
    public PyType visitPyTypeVarTupleType(PyTypeVarTupleType typeVarTupleType) {
        return typeVarTupleType;
    }

    @Override
    public PyType visitPyTypeParameterType(PyTypeParameterType typeParameterType) {
        return typeParameterType;
    }

    @Override
    public PyType visitPyUnpackedTupleType(PyUnpackedTupleType unpackedTupleType) {
        return new PyUnpackedTupleTypeImpl(
            ContainerUtil.map(unpackedTupleType.getElementTypes(), this::clone),
            unpackedTupleType.isUnbound()
        );
    }

    @Override
    public PyType visitPyCallableParameterListType(PyCallableParameterListType callableParameterListType) {
        return new PyCallableParameterListTypeImpl(
            ContainerUtil.map(callableParameterListType.getParameters(), parameter -> {
                var clonedType = clone(parameter.getType(myTypeEvalContext));
                var parameterPsi = parameter.getParameter();
                if (parameterPsi != null) {
                    // Keep PSI-backed parameters lazy to avoid loading the AST for their default values.
                    return PyCallableParameterImpl.psi(parameterPsi, clonedType);
                }
                return new PyCallableParameterImpl(
                    parameter.getName(),
                    Ref.create(clonedType),
                    parameter.getDefaultValue(),
                    parameter.getDefaultValueText(),
                    null,
                    parameter.isPositionalContainer(),
                    parameter.isKeywordContainer(),
                    parameter.isSelf(),
                    parameter.isKeywordOnlySeparator(),
                    parameter.isPositionOnlySeparator(),
                    parameter.getDeclarationElement()
                );
            })
        );
    }

    @Override
    public PyType visitPyUnpackedTypedDictType(PyUnpackedTypedDictType unpackedTypedDictType) {
        return new PyUnpackedTypedDictTypeImpl(clone(unpackedTypedDictType.getTypedDictType()));
    }
}
