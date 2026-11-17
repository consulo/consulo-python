// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PySequencePattern;
import com.jetbrains.python.psi.PySingleStarPattern;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PySequencePatternImpl extends PyElementImpl implements PySequencePattern, PyCaptureContext {
    public PySequencePatternImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPySequencePattern(this);
    }

    public List<PyPattern> getComponents() {
        return getElements();
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyType sequenceCaptureType = getSequenceCaptureType(context);
        List<@Nullable PyType> types = new ArrayList<>();
        for (PyPattern pattern : getElements()) {
            if (pattern instanceof PySingleStarPattern singleStarPattern) {
                types.addAll(getCapturedTypesFromSequenceType(singleStarPattern, sequenceCaptureType, context));
            }
            else {
                types.add(context.getType(pattern));
            }
        }

        PyType expectedType = isHeterogeneousTuple(sequenceCaptureType)
            ? PyTupleType.create(this, types)
            : wrapInSequenceType(PyUnionType.unionOrUnknown(types));

        if (sequenceCaptureType == null) {
            return expectedType;
        }
        List<@Nullable PyType> intersected = new ArrayList<>();
        for (PyType it : PyTypeUtil.getCompositeComponents(sequenceCaptureType)) {
            intersected.add(intersect(it, expectedType, context));
        }
        return PyUnionType.unionOrUnknown(intersected);
    }

    @Override
    public boolean canExcludePatternType(TypeEvalContext context) {
        List<PyPattern> elements = getElements();
        if (elements.size() == 1 && elements.get(0) instanceof PySingleStarPattern) {
            return true;
        }
        for (PyPattern it : elements) {
            if (!PyClassPatternImpl.isExhaustive(it, context)) {
                return false;
            }
        }
        PyType captureType = getSequenceCaptureType(context);
        if (captureType == null) {
            return false;
        }
        for (PyType type : PyTypeUtil.getCompositeComponents(captureType)) {
            if (!isHeterogeneousTuple(type)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context) {
        PyType sequenceType = getSequenceCaptureType(context);
        if (sequenceType == null) {
            return null;
        }

        // This is done to skip group- and as-patterns
        PsiElement sequenceMember = findParentInFile(pattern);
        if (sequenceMember instanceof PySingleStarPattern singleStarPattern) {
            List<@Nullable PyType> types = new ArrayList<>();
            for (PyType it : PyTypeUtil.getCompositeComponents(sequenceType)) {
                types.addAll(getCapturedTypesFromSequenceType(singleStarPattern, it, context));
            }
            return wrapInListType(PyUnionType.unionOrUnknown(types));
        }

        int idx = getElements().indexOf(sequenceMember);

        List<@Nullable PyType> types = new ArrayList<>();
        for (PyType it : PyTypeUtil.getCompositeComponents(sequenceType)) {
            types.add(getElementTypeSkippingStar(it, idx, context));
        }
        return PyUnionType.unionOrUnknown(types);
    }

    /**
     * {@code pattern.findParentInFile(withSelf = true) { el -> this === el.parent }}
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

    private @Nullable PyType getElementTypeSkippingStar(@Nullable PyType sequence, int idx, TypeEvalContext context) {
        if (isHeterogeneousTuple(sequence)) {
            PyTupleType tupleType = (PyTupleType)sequence;
            List<PyPattern> elements = getElements();
            int starIdx = -1;
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i) instanceof PySingleStarPattern) {
                    starIdx = i;
                    break;
                }
            }
            if (starIdx == -1 || idx < starIdx) {
                return tupleType.getElementType(idx);
            }
            else {
                int starSpan = tupleType.getElementCount() - elements.size();
                return tupleType.getElementType(idx + starSpan);
            }
        }
        else {
            PyType upcast = PyTypeUtil.convertToType(sequence, "typing.Sequence", this, context);
            return upcast instanceof PyClassType classType ? classType.getIteratedItemType() : null;
        }
    }

    /**
     * Similar to {@link PyCaptureContext#getCaptureType},
     * but only chooses types that would match to typing.Sequence, and have correct length
     */
    private @Nullable PyType getSequenceCaptureType(TypeEvalContext context) {
        PyType captureTypes = PyCaptureContext.getCaptureType(this, context);

        List<String> excluded = List.of(PyNames.FQN.STR, PyNames.FQN.BYTES, PyNames.FQN.BYTEARRAY);
        List<@Nullable PyType> potentialMatchingTypes = new ArrayList<>();
        for (PyType it : PyTypeUtil.getCompositeComponents(captureTypes)) {
            if (it instanceof PyClassType classType && excluded.contains(classType.getClassQName())) {
                continue;
            }
            if (PyTypeUtil.convertToType(it, "typing.Sequence", this, context) == null) {
                continue;
            }
            potentialMatchingTypes.add(it);
        }

        List<PyPattern> elements = getElements();
        boolean hasStar = false;
        for (PyPattern it : elements) {
            if (it instanceof PySingleStarPattern) {
                hasStar = true;
                break;
            }
        }
        List<@Nullable PyType> types = new ArrayList<>();
        for (PyType it : potentialMatchingTypes) {
            PyType mapped = isHeterogeneousTuple(it) ? takeIfSizeMatches((PyTupleType)it, elements.size(), hasStar) : it;
            if (mapped != null) {
                types.add(mapped);
            }
        }
        return PyUnionType.unionOrUnknown(types);
    }

    public @Nullable PyType wrapInListType(@Nullable PyType elementType) {
        PyClass list = PyBuiltinCache.getInstance(this).getClass("list");
        if (list == null) {
            return null;
        }
        return new PyCollectionTypeImpl(list, false, Collections.singletonList(PyTypeUtil.widenLiteralAndNumeric(elementType)));
    }

    public @Nullable PyType wrapInSequenceType(@Nullable PyType elementType) {
        PyClass sequence = PyPsiFacade.getInstance(getProject()).createClassByQName("typing.Sequence", this);
        if (sequence == null) {
            return null;
        }
        return new PyCollectionTypeImpl(sequence, false, Collections.singletonList(PyTypeUtil.widenLiteralAndNumeric(elementType)));
    }

    private static List<@Nullable PyType> getCapturedTypesFromSequenceType(PySingleStarPattern pattern,
                                                                           @Nullable PyType sequenceType,
                                                                           TypeEvalContext context) {
        if (isHeterogeneousTuple(sequenceType)) {
            PyTupleType tupleType = (PyTupleType)sequenceType;
            if (!(pattern.getParent() instanceof PySequencePattern sequenceParent)) {
                return List.of();
            }
            int idx = sequenceParent.getElements().indexOf(pattern);
            return tupleType.getElementTypes().subList(idx, idx + tupleType.getElementCount() - sequenceParent.getElements().size() + 1);
        }
        PyType upcast = PyTypeUtil.convertToType(sequenceType, "typing.Sequence", pattern, context);
        if (upcast instanceof PyClassType classType && classType.isParameterized()) {
            return Collections.singletonList(classType.getIteratedItemType());
        }
        return List.of();
    }

    private static boolean isHeterogeneousTuple(@Nullable PyType type) {
        return type instanceof PyTupleType tupleType && !tupleType.isHomogeneous();
    }

    private static @Nullable PyTupleType takeIfSizeMatches(PyTupleType type, int desiredSize, boolean hasStar) {
        boolean hasUnpacked = false;
        for (PyType it : type.getElementTypes()) {
            if (it instanceof PyUnpackedTupleType) {
                hasUnpacked = true;
                break;
            }
        }
        if (hasUnpacked) {
            int variadicElementsCount = desiredSize - type.getElementTypes().size() + 1;
            if (variadicElementsCount >= 0) {
                return expandVariadics(type, variadicElementsCount);
            }
        }
        else {
            if (hasStar && desiredSize <= type.getElementCount() || desiredSize == type.getElementCount()) {
                return type;
            }
        }
        return null;
    }

    private static PyTupleType expandVariadics(PyTupleType type, int variadicElementCount) {
        if (type.isHomogeneous()) {
            throw new IllegalArgumentException("Supplied tuple must not be homogeneous: " + type);
        }
        if (variadicElementCount < 0) {
            throw new IllegalArgumentException("Supplied variadic element count must not be negative: " + variadicElementCount);
        }

        List<PyType> elementTypes = type.getElementTypes();
        int unpackedTupleIndex = -1;
        for (int i = 0; i < elementTypes.size(); i++) {
            if (elementTypes.get(i) instanceof PyUnpackedTupleType) {
                unpackedTupleIndex = i;
                break;
            }
        }
        PyUnpackedTupleType unpackedTupleType = (PyUnpackedTupleType)elementTypes.get(unpackedTupleIndex);
        assert unpackedTupleType.isUnbound();

        List<@Nullable PyType> adjustedTupleElementTypes = new ArrayList<>();
        adjustedTupleElementTypes.addAll(elementTypes.subList(0, unpackedTupleIndex));
        for (int i = 0; i < variadicElementCount; i++) {
            adjustedTupleElementTypes.add(unpackedTupleType.getElementTypes().get(0));
        }
        adjustedTupleElementTypes.addAll(elementTypes.subList(unpackedTupleIndex + 1, elementTypes.size()));
        return new PyTupleType(type.getPyClass(), adjustedTupleElementTypes, false);
    }

    private static @Nullable PyType intersect(@Nullable PyType type1, @Nullable PyType type2, TypeEvalContext context) {
        // TODO: replace with proper PyIntersectionType.intersect when it appears
        if (type1 == null) {
            return type2;
        }
        if (type2 == null) {
            return type1;
        }
        if (type1 instanceof PyTupleType tuple1 && type2 instanceof PyTupleType tuple2) {
            if (tuple1.getElementCount() != tuple2.getElementCount()) {
                return PyNeverType.NEVER;
            }
            List<PyType> elementTypes1 = tuple1.getElementTypes();
            List<PyType> elementTypes2 = tuple2.getElementTypes();
            List<@Nullable PyType> elements = new ArrayList<>();
            for (int i = 0; i < Math.min(elementTypes1.size(), elementTypes2.size()); i++) {
                elements.add(intersect(elementTypes1.get(i), elementTypes2.get(i), context));
            }
            return PyTupleType.create(tuple1.getPyClass(), elements);
        }
        return PyTypeChecker.match(type1, type2, context) ? type2 : type1;
    }
}
