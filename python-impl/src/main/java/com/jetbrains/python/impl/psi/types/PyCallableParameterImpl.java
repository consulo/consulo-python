// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeRendererFeature;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class PyCallableParameterImpl implements PyCallableParameter {
    private final @Nullable String myName;
    private final @Nullable Ref<@Nullable PyType> myType;
    private final @Nullable PyExpression myDefaultValue;
    private final @Nullable String myDefaultValueText;
    private final @Nullable PyParameter parameter;
    private final boolean myIsPositionalContainer;
    private final boolean myIsKeywordContainer;
    private final boolean myIsSelf;
    private final boolean myIsKeywordOnlySeparator;
    private final boolean myIsPositionalOnlySeparator;
    private final @Nullable PsiElement myDeclarationElement;

    public PyCallableParameterImpl(@Nullable String myName,
                                   @Nullable Ref<@Nullable PyType> myType,
                                   @Nullable PyExpression myDefaultValue,
                                   @Nullable String myDefaultValueText,
                                   @Nullable PyParameter parameter,
                                   boolean myIsPositionalContainer,
                                   boolean myIsKeywordContainer,
                                   boolean myIsSelf,
                                   boolean myIsKeywordOnlySeparator,
                                   boolean myIsPositionalOnlySeparator,
                                   @Nullable PsiElement myDeclarationElement) {
        this.myName = myName;
        this.myType = myType;
        this.myDefaultValue = myDefaultValue;
        this.myDefaultValueText = myDefaultValueText;
        this.parameter = parameter;
        this.myIsPositionalContainer = myIsPositionalContainer;
        this.myIsKeywordContainer = myIsKeywordContainer;
        this.myIsSelf = myIsSelf;
        this.myIsKeywordOnlySeparator = myIsKeywordOnlySeparator;
        this.myIsPositionalOnlySeparator = myIsPositionalOnlySeparator;
        this.myDeclarationElement = myDeclarationElement;
        if (myType != null) {
            PyAnyType.validate(myType.get());
        }
    }

    @Override
    public @Nullable PyParameter getParameter() {
        return parameter;
    }

    @Override
    public @Nullable String getName() {
        return myName != null ? myName : parameter != null ? parameter.getName() : null;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context) {
        if (myType != null) {
            return myType.get();
        }
        else if (parameter instanceof PyNamedParameter namedParameter) {
            return context.getType(namedParameter);
        }
        else {
            return PyAnyType.getUnknown();
        }
    }

    @Override
    public @Nullable PsiElement getDeclarationElement() {
        return myDeclarationElement != null ? myDeclarationElement : parameter;
    }

    @Override
    public @Nullable PyExpression getDefaultValue() {
        return parameter == null ? myDefaultValue : parameter.getDefaultValue();
    }

    @Override
    public boolean hasDefaultValue() {
        return parameter != null ? parameter.hasDefaultValue() : (myDefaultValue != null || myDefaultValueText != null);
    }

    @Override
    public @Nullable String getDefaultValueText() {
        if (parameter != null) {
            return parameter.getDefaultValueText();
        }
        else if (myDefaultValue != null) {
            return myDefaultValue.getText();
        }
        else if (myDefaultValueText != null) {
            return myDefaultValueText;
        }
        else {
            return null;
        }
    }

    @Override
    public boolean isPositionalContainer() {
        return myIsPositionalContainer || parameter instanceof PyNamedParameter namedParameter && namedParameter.isPositionalContainer();
    }

    @Override
    public boolean isKeywordContainer() {
        return myIsKeywordContainer || parameter instanceof PyNamedParameter namedParameter && namedParameter.isKeywordContainer();
    }

    @Override
    public boolean isSelf() {
        return myIsSelf || parameter != null && parameter.isSelf();
    }

    @Override
    public boolean isPositionOnlySeparator() {
        return parameter instanceof PySlashParameter || myIsPositionalOnlySeparator;
    }

    @Override
    public boolean isKeywordOnlySeparator() {
        return parameter instanceof PySingleStarParameter || myIsKeywordOnlySeparator;
    }

    @Override
    public String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context) {
        return getPresentableText(includeDefaultValue, context, Collections.emptySet(), PyTypeUtilKt::isAnyOrUnknown);
    }

    @Override
    public String getPresentableText(boolean includeDefaultValue,
                                     @Nullable TypeEvalContext context,
                                     Set<PyTypeRendererFeature> typeRendererFeatures,
                                     Predicate<@Nullable PyType> typeFilter) {
        if (!(parameter instanceof PyNamedParameter) && parameter != null) {
            return PyUtil.getReadableRepr(parameter, false);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(ParamHelper.getNameInSignature(this));

        boolean renderedAsTyped = false;
        if (context != null) {
            PyType argumentType = getArgumentType(context);
            if (!typeFilter.test(argumentType)) {
                builder.append(": ");
                builder.append(typeRendererFeatures.isEmpty()
                                   ? PythonDocumentationProvider.getTypeName(argumentType, context)
                                   : PythonDocumentationProvider.getTypeName(argumentType, context,
                                                                             typeRendererFeatures.toArray(new PyTypeRendererFeature[0])));
                renderedAsTyped = true;
            }
        }

        if (includeDefaultValue) {
            String defaultValuePart = ParamHelper.getDefaultValuePartInSignature(getDefaultValueText(), renderedAsTyped);
            builder.append(defaultValuePart != null ? defaultValuePart : "");
        }
        return builder.toString();
    }

    /**
     * Returns the expected type for an argument passed to this parameter.
     * <p>
     * For most parameters, this is the same as {@link #getType}.
     * However, for variadic parameters, this method applies special handling:
     * <ul>
     * <li>For positional containers ({@code *args}):
     *   <ul>
     *   <li>If the type is {@code tuple[T, ...]}, returns {@code T} (the element type)</li>
     *   <li>If the type is {@code *tuple[T1, T2, ...]}, returns the unpacked tuple type itself</li>
     *   </ul>
     * </li>
     * <li>For keyword containers ({@code **kwargs}):
     *   <ul>
     *   <li>If the type is a collection (e.g., {@code dict[str, V]}), returns {@code V} (the value type)</li>
     *   <li>If the type is {@code Unpack[TypedDict]}, returns the unpacked TypedDict type</li>
     *   </ul>
     * </li>
     * </ul>
     *
     * @param context the type evaluation context
     * @return the expected type of the argument, or {@code null} if the type cannot be determined
     */
    @Override
    public @Nullable PyType getArgumentType(TypeEvalContext context) {
        PyType parameterType = getType(context);

        if (isPositionalContainer() && parameterType instanceof PyTupleType tupleType) {
            // *args: str is equivalent to *args: *tuple[str, ...]
            // *args: *Ts is equivalent to *args: *tuple[*Ts]
            // Convert its type to a more general form of an unpacked tuple
            PyUnpackedTupleType unpackedTupleType = tupleType.asUnpackedTupleType();
            if (unpackedTupleType.isUnbound()) {
                return unpackedTupleType.getElementTypes().get(0);
            }
            else {
                return unpackedTupleType;
            }
        }
        else if (isKeywordContainer()) {
            if (parameterType instanceof PyClassType classType && classType.isParameterized()) {
                List<PyType> typeArguments = classType.getTypeArguments();
                return typeArguments.size() > 1 ? typeArguments.get(1) : null;
            }
            else {
                return parameterType instanceof PyUnpackedTypedDictType ? parameterType : parameterType;
            }
        }
        else {
            return parameterType;
        }
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        PyCallableParameterImpl that = (PyCallableParameterImpl)other;
        return Objects.equals(parameter, that.parameter) &&
            Objects.equals(myName, that.myName) &&
            Objects.equals(myType != null ? myType.get() : null, that.myType != null ? that.myType.get() : null) &&
            myIsSelf == that.myIsSelf &&
            myIsPositionalContainer == that.myIsPositionalContainer &&
            myIsKeywordContainer == that.myIsKeywordContainer &&
            myIsKeywordOnlySeparator == that.myIsKeywordOnlySeparator &&
            myIsPositionalOnlySeparator == that.myIsPositionalOnlySeparator &&
            Objects.equals(myDefaultValue, that.myDefaultValue) &&
            Objects.equals(myDefaultValueText, that.myDefaultValueText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            parameter,
            myName,
            myType != null ? myType.get() : null,
            myIsSelf,
            myIsPositionalContainer,
            myIsKeywordContainer,
            myIsKeywordOnlySeparator,
            myIsPositionalOnlySeparator,
            myDefaultValue,
            myDefaultValueText
        );
    }

    public static PyCallableParameter nonPsi(@Nullable PyType type) {
        return nonPsi(null, type, (PyExpression)null);
    }

    public static PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type) {
        return nonPsi(name, type, (PyExpression)null);
    }

    public static PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type, @Nullable PyExpression defaultValue) {
        return new PyCallableParameterImpl(name, Ref.create(type), defaultValue, null, null, false, false, false, false, false, null);
    }

    public static PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type, @Nullable String defaultValueText) {
        return new PyCallableParameterImpl(name, Ref.create(type), null, defaultValueText, null, false, false, false, false, false, null);
    }

    public static PyCallableParameter nonPsi(@Nullable String name,
                                             @Nullable PyType type,
                                             @Nullable PyExpression defaultValue,
                                             PsiElement declarationElement) {
        return new PyCallableParameterImpl(name, Ref.create(type), defaultValue, null, null, false, false, false, false, false,
                                           declarationElement);
    }

    public static PyCallableParameter selfNonPsi(@Nullable PyType type) {
        return new PyCallableParameterImpl(PyNames.CANONICAL_SELF, Ref.create(type), null, null, null, false, false, true, false, false,
                                           null);
    }

    public static PyCallableParameter positionalContainerNonPsi(@Nullable String name, @Nullable PyType type) {
        return new PyCallableParameterImpl(name, Ref.create(type), null, null, null, true, false, false, false, false, null);
    }

    public static PyCallableParameter keywordContainerNonPsi(@Nullable String name, @Nullable PyType type) {
        return new PyCallableParameterImpl(name, Ref.create(type), null, null, null, false, true, false, false, false, null);
    }

    public static PyCallableParameter keywordOnlySeparatorNonPsi() {
        return new PyCallableParameterImpl(null, null, null, null, null, false, false, false, true, false, null);
    }

    public static PyCallableParameter positionalOnlySeparatorNonPsi() {
        return new PyCallableParameterImpl(null, null, null, null, null, false, false, false, false, true, null);
    }

    public static PyCallableParameter psi(PyParameter parameter) {
        return new PyCallableParameterImpl(null, null, null, null, parameter, false, false, false, false, false, null);
    }

    public static PyCallableParameter psi(PyParameter parameter, @Nullable PyType type) {
        return new PyCallableParameterImpl(null, Ref.create(type), null, null, parameter, false, false, false, false, false, null);
    }
}
