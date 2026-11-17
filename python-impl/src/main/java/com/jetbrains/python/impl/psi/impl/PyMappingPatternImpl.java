// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDoubleStarPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeyValuePattern;
import com.jetbrains.python.psi.PyMappingPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PyMappingPatternImpl extends PyElementImpl implements PyMappingPattern, PyCaptureContext {
    public PyMappingPatternImpl(ASTNode astNode) {
        super(astNode);
    }

    private List<PyPattern> getElements() {
        return Arrays.asList(findChildrenByClass(PyPattern.class));
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyMappingPattern(this);
    }

    public List<PyPattern> getComponents() {
        return getElements();
    }

    @Override
    public boolean canExcludePatternType(TypeEvalContext context) {
        List<PyPattern> elements = getElements();
        return elements.size() == 1 && elements.get(0) instanceof PyDoubleStarPattern;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        List<@Nullable PyType> keyTypes = new ArrayList<>();
        List<@Nullable PyType> valueTypes = new ArrayList<>();
        for (PyPattern element : getElements()) {
            if (!(element instanceof PyKeyValuePattern it)) {
                continue;
            }
            keyTypes.add(context.getType(it.getKeyPattern()));
            PyPattern valuePattern = it.getValuePattern();
            if (valuePattern != null) {
                valueTypes.add(context.getType(valuePattern));
            }
        }

        PyType patternMappingType = wrapInMappingType(PyUnionType.unionOrUnknown(keyTypes), PyUnionType.unionOrUnknown(valueTypes));

        List<@Nullable PyType> matchingComponents = new ArrayList<>();
        for (PyType captureType : PyTypeUtil.getCompositeComponents(PyCaptureContext.getCaptureType(this, context))) {
            PyType mappingType = PyTypeUtil.convertToType(captureType, "typing.Mapping", this, context);
            if (mappingType == null) {
                continue;
            }
            if (PyTypeChecker.match(mappingType, patternMappingType, context)) {
                matchingComponents.add(captureType);
            }
        }
        PyType filteredType = matchingComponents.isEmpty() ? null : PyUnionType.unionOrUnknown(matchingComponents);

        return filteredType != null ? filteredType : patternMappingType;
    }

    @Override
    public @Nullable PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context) {
        PsiElement sequenceMember = findParentInFile(pattern);
        if (sequenceMember instanceof PyDoubleStarPattern) {
            PyType mappingType = PyTypeUtil.convertToType(context.getType(this), "typing.Mapping", pattern, context);
            if (mappingType instanceof PyClassType classType && classType.isParameterized()) {
                PyClass dict = PyBuiltinCache.getInstance(pattern).getClass("dict");
                if (dict == null) {
                    return null;
                }
                return new PyCollectionTypeImpl(dict, false, classType.getTypeArguments());
            }
            return null;
        }

        if (!(sequenceMember instanceof PyKeyValuePattern keyValuePattern)) {
            return null;
        }

        List<@Nullable PyType> types = new ArrayList<>();
        for (PyType possibleMapping : PyTypeUtil.getCompositeComponents(PyCaptureContext.getCaptureType(this, context))) {
            types.add(getValueType(possibleMapping, keyValuePattern, context));
        }
        return PyUnionType.unionOrUnknown(types);
    }

    /**
     * {@code pattern.findParentInFile(withSelf = true) { this === it.parent }}
     */
    private @Nullable PsiElement findParentInFile(PsiElement pattern) {
        PsiElement current = pattern;
        while (current != null) {
            if (this == current.getParent()) {
                return current;
            }
            if (current instanceof PsiFile) {
                break;
            }
            current = current.getParent();
        }
        return null;
    }

    private @Nullable PyType wrapInMappingType(@Nullable PyType keyType, @Nullable PyType valueType) {
        PyClass sequence = PyPsiFacade.getInstance(getProject()).createClassByQName("typing.Mapping", this);
        if (sequence == null) {
            return PyAnyType.getUnknown();
        }
        return new PyCollectionTypeImpl(sequence, false, Arrays.asList(PyTypeUtil.widenLiteralAndNumeric(keyType),
            PyTypeUtil.widenLiteralAndNumeric(valueType)));
    }

    private static @Nullable PyType getValueType(@Nullable PyType type, PyKeyValuePattern sequenceMember, TypeEvalContext context) {
        if (type instanceof PyTypedDictType typedDictType) {
            String key = getKeyString(sequenceMember, context);
            if (key != null) {
                return typedDictType.getElementType(key, context);
            }
        }
        PyType mappingType = PyTypeUtil.convertToType(type, "typing.Mapping", sequenceMember, context);
        if (mappingType == null) {
            return PyNeverType.NEVER;
        }
        if (mappingType instanceof PyClassType classType && classType.isParameterized()) {
            return classType.getTypeArguments().get(1);
        }
        return null;
    }

    private static @Nullable String getKeyString(PyKeyValuePattern pattern, TypeEvalContext context) {
        PyType keyType = context.getType(pattern.getKeyPattern());
        if (keyType instanceof PyLiteralType literalType) {
            return literalType.getStringValue();
        }
        return null;
    }
}
