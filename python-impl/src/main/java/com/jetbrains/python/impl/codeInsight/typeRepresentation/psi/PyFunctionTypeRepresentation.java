// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation.psi;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstElementKt;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.PyTypeRepresentationResolveKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeVarTypeImpl;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.lazy.LazyValue;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PyFunctionTypeRepresentation extends PyElementImpl implements PyExpression {
    private final Supplier<@Nullable QualifiedName> myFunctionName = LazyValue.nullable(() -> {
        // Function name is the first PyExpression child that comes before the type parameter list (if present)
        // or before the parameter list (if no type parameters)
        PsiElement typeParameterList = getTypeParameterList();
        PsiElement stopAt = typeParameterList != null ? typeParameterList : getParameterList();
        for (PsiElement child : getChildren()) {
            if (child == stopAt) {
                break;
            }
            if (child instanceof PyExpression) {
                return QualifiedName.fromDottedString(child.getText());
            }
        }
        return null;
    });

    public PyFunctionTypeRepresentation(ASTNode astNode) {
        super(astNode);
    }

    public @Nullable QualifiedName getFunctionName() {
        return myFunctionName.get();
    }

    public @Nullable PyTypeParameterList getTypeParameterList() {
        return findChildByClass(PyTypeParameterList.class);
    }

    public PyParameterListRepresentation getParameterList() {
        return findNotNullChildByClass(PyParameterListRepresentation.class);
    }

    public @Nullable PyExpression getReturnType() {
        // Return type is after the -> token
        ASTNode arrow = getNode().findChildByType(PyTokenTypes.RARROW);
        if (arrow == null) {
            return null;
        }
        ASTNode sibling = arrow.getTreeNext();
        while (sibling != null) {
            if (sibling.getPsi() instanceof PyExpression expression) {
                return expression;
            }
            sibling = sibling.getTreeNext();
        }
        return null;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression returnTypeExpr = getReturnType();
        if (returnTypeExpr == null) {
            return null;
        }

        // Create type variables from type parameter list
        Map<String, PyTypeVarType> typeVarMap = createTypeVarMap(context);

        // Parse callable parameters from the signature (shared by both callable and function types)
        PyType retType = resolveTypeExpression(returnTypeExpr, context, typeVarMap);
        // If we have a function name, this is a 'def' type - try to resolve to PyFunctionType
        QualifiedName qualifiedFunctionName = getFunctionName();
        if (qualifiedFunctionName != null) {
            PyFunction resolvedFunction =
                PyTypeRepresentationResolveKt.resolveTypeRepresentationName(qualifiedFunctionName, this, context, PyFunction.class);

            // If we resolved the function, create PyFunctionType with the signature from the representation
            if (resolvedFunction != null) {
                // Create a custom PyFunctionType that uses our return type, not the function's definition
                List<PyCallableParameter> callableParams = parseCallableParameters(context, typeVarMap, resolvedFunction.getParameterList());
                return new PyFunctionTypeImpl(resolvedFunction, callableParams) {
                    @Override
                    public @Nullable PyType getReturnType(TypeEvalContext context) {
                        return retType;
                    }
                };
            }
            // Fall through to create PyCallableType if resolution failed
        }

        // Create PyCallableType from the signature (for both unresolved functions and plain callables)
        List<PyCallableParameter> callableParams = parseCallableParameters(context, typeVarMap, null);
        return new PyCallableTypeImpl(callableParams, retType);
    }

    private Map<String, PyTypeVarType> createTypeVarMap(TypeEvalContext context) {
        PyTypeParameterList typeParams = getTypeParameterList();
        if (typeParams == null) {
            return Collections.emptyMap();
        }
        Map<String, PyTypeVarType> result = new HashMap<>();

        for (PyTypeParameter param : typeParams.getTypeParameters()) {
            String paramName = param.getName();
            if (paramName == null) {
                continue;
            }

            // Determine bound type from the type parameter's bound expression. A type parameter without a bound
            // has an unknown bound, the same as in PyTypingTypeProvider.
            PyExpression boundExpression = param.getBoundExpression();
            PyType boundType = boundExpression != null ? resolveTypeExpression(boundExpression, context, Collections.emptyMap()) : null;
            if (boundType == null) {
                boundType = PyAnyType.getUnknown();
            }

            // Create type variable - PyTypeVarTypeImpl(name, constraints, bound, defaultType, variance)
            PyTypeVarType typeVar = new PyTypeVarTypeImpl(
                paramName,
                Collections.emptyList(), // constraints
                boundType, // bound
                null, // defaultType (Ref<PyType>?)
                PyVariance.INVARIANT // variance
            );

            result.put(paramName, typeVar);
        }

        return result;
    }

    private List<PyCallableParameter> parseCallableParameters(TypeEvalContext context,
                                                              Map<String, PyTypeVarType> typeVarMap,
                                                              @Nullable PyParameterList resolvedFunctionParameters) {
        List<PyCallableParameter> result = new ArrayList<>();
        for (PsiElement param : getParameterList().getParameters()) {
            result.add(parseCallableParameter(param, context, typeVarMap, resolvedFunctionParameters));
        }
        return result;
    }

    private PyCallableParameter parseCallableParameter(PsiElement param,
                                                       TypeEvalContext context,
                                                       Map<String, PyTypeVarType> typeVarMap,
                                                       @Nullable PyParameterList resolvedFunctionParameters) {
        if (param instanceof PySlashParameter slashParameter) {
            return PyCallableParameterImpl.psi(slashParameter);
        }
        else if (param instanceof PyNamedParameterTypeRepresentation namedParam) {
            boolean isSelf = false;
            if (resolvedFunctionParameters != null) {
                String name = namedParam.getName();
                PyNamedParameter parameter = resolvedFunctionParameters.findParameterByName(name != null ? name : "");
                isSelf = parameter != null && parameter.isSelf();
            }
            PyExpression typeExpression = namedParam.getTypeExpression();
            PyType paramType = typeExpression != null ? resolveTypeExpression(typeExpression, context, typeVarMap) : null;
            if (paramType == null) {
                paramType = PyAnyType.getUnknown();
            }
            return new PyCallableParameterImpl(namedParam.getName(), Ref.create(paramType), namedParam.getDefaultValue(), null, null,
                                               false, false, isSelf, false, false, null);
        }
        else if (param instanceof PyStarExpression starExpression) {
            // *args parameter
            // Check if it contains a named parameter
            PyNamedParameterTypeRepresentation namedParam = PyAstElementKt.findChildByClass(starExpression, PyNamedParameterTypeRepresentation.class);
            if (namedParam != null) {
                // *args: type
                String paramName = namedParam.getName();
                PyExpression typeExpression = namedParam.getTypeExpression();
                PyType paramType = typeExpression != null ? resolveTypeExpression(typeExpression, context, typeVarMap) : null;
                return PyCallableParameterImpl.positionalContainerNonPsi(paramName, paramType != null ? paramType : PyAnyType.getUnknown());
            }
            else {
                // Unnamed *args: *type
                PyExpression innerExpr = starExpression.getExpression();
                PyType paramType = innerExpr != null ? resolveTypeExpression(innerExpr, context, typeVarMap) : null;
                return PyCallableParameterImpl.positionalContainerNonPsi(null, paramType != null ? paramType : PyAnyType.getUnknown());
            }
        }
        else if (param instanceof PyDoubleStarExpression doubleStarExpression) {
            // **kwargs parameter
            // Check if it contains a named parameter
            PyNamedParameterTypeRepresentation namedParam = PyAstElementKt.findChildByClass(doubleStarExpression, PyNamedParameterTypeRepresentation.class);
            if (namedParam != null) {
                String paramName = namedParam.getName();
                PyExpression it = namedParam.getTypeExpression();
                PyType paramType = null;
                if (it != null) {
                    if (it instanceof PyDoubleStarExpression unpackedExpression) {
                        // Named kwargs unpacked: `**name: **type`
                        PyExpression unpacked = unpackedExpression.getExpression();
                        paramType = unpacked != null ? resolveTypeExpression(unpacked, context, typeVarMap) : null;
                    }
                    else {
                        // Named kwargs: `**name: type`, adapt to `dict`
                        PyBuiltinCache builtins = PyBuiltinCache.getInstance(it);
                        PyClassType dictType = builtins.getDictType();
                        if (dictType != null) {
                            List<@Nullable PyType> elementTypes = new ArrayList<>();
                            elementTypes.add(builtins.getStrType());
                            elementTypes.add(resolveTypeExpression(it, context, typeVarMap));
                            paramType = new PyCollectionTypeImpl(dictType.getPyClass(), false, elementTypes);
                        }
                    }
                }
                return PyCallableParameterImpl.keywordContainerNonPsi(paramName, paramType != null ? paramType : PyAnyType.getUnknown());
            }
            else {
                // Unnamed kwargs: `**type`
                PyExpression innerExpr = doubleStarExpression.getExpression();
                PyType paramType = innerExpr != null ? resolveTypeExpression(innerExpr, context, typeVarMap) : null;
                return PyCallableParameterImpl.keywordContainerNonPsi(null, paramType != null ? paramType : PyAnyType.getUnknown());
            }
        }
        else if (param instanceof PyExpression expression) {
            PyType paramType = resolveTypeExpression(expression, context, typeVarMap);
            return PyCallableParameterImpl.nonPsi(paramType != null ? paramType : PyAnyType.getUnknown());
        }
        else {
            return PyCallableParameterImpl.nonPsi(PyAnyType.getUnknown());
        }
    }

    private static @Nullable PyType resolveTypeExpression(PyExpression expr, TypeEvalContext context, Map<String, PyTypeVarType> typeVarMap) {
        // Check if this is a reference to a type parameter
        if (expr instanceof PyReferenceExpression referenceExpression && referenceExpression.getQualifier() == null) {
            String name = referenceExpression.getName();
            if (name != null) {
                PyTypeVarType typeVar = typeVarMap.get(name);
                if (typeVar != null) {
                    return typeVar;
                }
            }
        }

        // Otherwise, resolve normally
        if (expr instanceof PyDoubleStarExpression doubleStarExpression) {
            PyExpression inner = doubleStarExpression.getExpression();
            Ref<PyType> typeRef = inner != null ? PyTypingTypeProvider.getType(inner, context) : null;
            PyType type = typeRef != null ? typeRef.get() : null;
            return type != null ? type : PyAnyType.getUnknown();
        }
        else {
            return PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.getType(expr, context));
        }
    }
}
