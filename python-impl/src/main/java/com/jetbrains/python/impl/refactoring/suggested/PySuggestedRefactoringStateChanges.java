// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.suggested;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringState;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringStateChanges;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringSupport;
import consulo.language.editor.refactoring.suggested.TextRangeUtils;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

class PySuggestedRefactoringStateChanges extends SuggestedRefactoringStateChanges {
    PySuggestedRefactoringStateChanges(PySuggestedRefactoringSupport support) {
        super(support);
    }

    @Override
    @RequiredReadAction
    public SuggestedRefactoringSupport.@Nullable Signature signature(PsiElement anchor, @Nullable SuggestedRefactoringState prevState) {
        return findStateChanges(anchor).signature(anchor, prevState);
    }

    @Override
    @RequiredReadAction
    public List<@Nullable TextRange> parameterMarkerRanges(PsiElement anchor) {
        return findStateChanges(anchor).parameterMarkerRanges(anchor);
    }

    @Override
    @RequiredReadAction
    public SuggestedRefactoringState updateState(SuggestedRefactoringState state, PsiElement anchor) {
        return findStateChanges(anchor).updateNewState(state, anchor, super.updateState(state, anchor));
    }

    @Override
    protected @Nullable Object guessParameterIdByMarkers(TextRange markerRange, SuggestedRefactoringState prevState) {
        Object id = super.guessParameterIdByMarkers(markerRange, prevState);
        if (id != null) {
            return id;
        }
        // findStateChanges(prevState.declaration) can't be used here since !prevState.declaration.isValid
        for (StateChangesInternal stateChanges : List.of(new ChangeSignatureStateChanges(this), RenameStateChanges.INSTANCE)) {
            Object guessed = stateChanges.guessParameterIdByMarker(markerRange, prevState);
            if (guessed != null) {
                return guessed;
            }
        }
        return null;
    }

    private StateChangesInternal findStateChanges(PsiElement declaration) {
        for (StateChangesInternal stateChanges : List.of(new ChangeSignatureStateChanges(this), RenameStateChanges.INSTANCE)) {
            if (stateChanges.isApplicable(declaration)) {
                return stateChanges;
            }
        }
        throw new NoSuchElementException("No state changes applicable to " + declaration);
    }

    private interface StateChangesInternal {
        boolean isApplicable(PsiElement declaration);

        @RequiredReadAction
        SuggestedRefactoringSupport.@Nullable Signature signature(PsiElement declaration, @Nullable SuggestedRefactoringState prevState);

        @RequiredReadAction
        List<@Nullable TextRange> parameterMarkerRanges(PsiElement declaration);

        default SuggestedRefactoringState updateNewState(
            SuggestedRefactoringState prevState,
            PsiElement declaration,
            SuggestedRefactoringState newState
        ) {
            return newState;
        }

        default @Nullable Object guessParameterIdByMarker(TextRange markerRange, SuggestedRefactoringState prevState) {
            return null;
        }
    }

    private static final class ChangeSignatureStateChanges implements StateChangesInternal {
        private static final Key<Map<RangeMarker, Object>> DISAPPEARED_RANGES =
            Key.create("PySuggestedRefactoringStateChanges.ChangeSignature.DISAPPEARED_RANGES");

        private final PySuggestedRefactoringStateChanges myMainStateChanges;

        ChangeSignatureStateChanges(PySuggestedRefactoringStateChanges mainStateChanges) {
            myMainStateChanges = mainStateChanges;
        }

        @Override
        public boolean isApplicable(PsiElement declaration) {
            return PySuggestedRefactoringSupport.Helper.isAvailableForChangeSignature(declaration);
        }

        @Override
        @RequiredReadAction
        public SuggestedRefactoringSupport.@Nullable Signature signature(
            PsiElement declaration,
            @Nullable SuggestedRefactoringState prevState
        ) {
            SuggestedRefactoringSupport.Signature signature = createSignatureData((PyFunction) declaration);
            if (signature == null) {
                return null;
            }
            return prevState == null ? signature : myMainStateChanges.matchParametersWithPrevState(signature, declaration, prevState);
        }

        @Override
        @RequiredReadAction
        public List<@Nullable TextRange> parameterMarkerRanges(PsiElement declaration) {
            List<@Nullable TextRange> result = new ArrayList<>();
            for (PyParameter parameter : ((PyFunction) declaration).getParameterList().getParameters()) {
                result.add(getParameterMarker(parameter));
            }
            return result;
        }

        @Override
        public SuggestedRefactoringState updateNewState(
            SuggestedRefactoringState prevState,
            PsiElement declaration,
            SuggestedRefactoringState newState
        ) {
            SuggestedRefactoringSupport.Signature initialSignature = prevState.getOldSignature();
            SuggestedRefactoringSupport.Signature prevSignature = prevState.getNewSignature();
            SuggestedRefactoringSupport.Signature newSignature = newState.getNewSignature();

            Set<Object> idsPresent = new HashSet<>();
            for (SuggestedRefactoringSupport.Parameter parameter : newSignature.getParameters()) {
                idsPresent.add(parameter.getId());
            }

            Map<RangeMarker, Object> prevDisappearedRanges = prevState.getAdditionalData().get(DISAPPEARED_RANGES);
            Map<RangeMarker, Object> disappearedRanges = new HashMap<>();
            if (prevDisappearedRanges != null) {
                for (Map.Entry<RangeMarker, Object> entry : prevDisappearedRanges.entrySet()) {
                    if (entry.getKey().isValid() && !idsPresent.contains(entry.getValue())) {
                        disappearedRanges.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            for (SuggestedRefactoringSupport.Parameter parameter : prevSignature.getParameters()) {
                Object id = parameter.getId();
                if (idsPresent.contains(id) || initialSignature.parameterById(id) == null) {
                    continue;
                }
                SuggestedRefactoringState.ParameterMarker marker = null;
                for (SuggestedRefactoringState.ParameterMarker parameterMarker : prevState.getParameterMarkers()) {
                    if (Objects.equals(parameterMarker.parameterId(), id)) {
                        marker = parameterMarker;
                        break;
                    }
                }
                if (marker != null && marker.rangeMarker().isValid()) {
                    disappearedRanges.put(marker.rangeMarker(), marker.parameterId());
                }
            }

            return newState.withAdditionalData(DISAPPEARED_RANGES, disappearedRanges);
        }

        @Override
        public @Nullable Object guessParameterIdByMarker(TextRange markerRange, SuggestedRefactoringState prevState) {
            Map<RangeMarker, Object> disappearedRanges = prevState.getAdditionalData().get(DISAPPEARED_RANGES);
            if (disappearedRanges == null) {
                return null;
            }
            for (Map.Entry<RangeMarker, Object> entry : disappearedRanges.entrySet()) {
                if (entry.getKey().isValid() && markerRange.equals(TextRangeUtils.asTextRange(entry.getKey()))) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @RequiredReadAction
        private SuggestedRefactoringSupport.@Nullable Signature createSignatureData(PyFunction function) {
            String name = function.getName();
            if (name == null) {
                return null;
            }
            List<SuggestedRefactoringSupport.Parameter> parametersData = new ArrayList<>();
            for (PyParameter parameter : function.getParameterList().getParameters()) {
                SuggestedRefactoringSupport.Parameter parameterData = createParameterData(parameter);
                if (parameterData == null) {
                    return null;
                }
                parametersData.add(parameterData);
            }
            return SuggestedRefactoringSupport.Signature.create(name, PyTypingTypeProvider.ANY, parametersData, null);
        }

        @RequiredReadAction
        private @Nullable TextRange getParameterMarker(PyParameter parameter) {
            PyNamedParameter asNamed = parameter.getAsNamed();
            if (asNamed != null) {
                if (asNamed.isPositionalContainer()) {
                    return getChildRange(parameter, PyTokenTypes.MULT);
                }
                if (asNamed.isKeywordContainer()) {
                    return getChildRange(parameter, PyTokenTypes.EXP);
                }
            }

            PsiElement next = PsiTreeUtil.skipWhitespacesForward(parameter);
            if (next != null) {
                IElementType elementType = PsiUtilCore.getElementType(next);
                if (elementType == PyTokenTypes.COMMA || elementType == PyTokenTypes.RPAR) {
                    return next.getTextRange();
                }
            }
            return null;
        }

        @RequiredReadAction
        private static @Nullable TextRange getChildRange(PsiElement element, IElementType type) {
            ASTNode child = element.getNode().findChildByType(type);
            return child != null ? child.getTextRange() : null;
        }

        @RequiredReadAction
        private static SuggestedRefactoringSupport.@Nullable Parameter createParameterData(PyParameter parameter) {
            String name;
            if (parameter instanceof PySlashParameter) {
                name = PySlashParameter.TEXT;
            }
            else if (parameter instanceof PySingleStarParameter) {
                name = PySingleStarParameter.TEXT;
            }
            else if (parameter instanceof PyNamedParameter namedParameter) {
                name = ParamHelper.getNameInSignature(namedParameter);
            }
            else {
                return null;
            }

            return new SuggestedRefactoringSupport.Parameter(
                new Object(),
                name,
                PyTypingTypeProvider.ANY,
                new PySuggestedRefactoringSupport.ParameterData(parameter.getDefaultValueText())
            );
        }
    }

    private static final class RenameStateChanges implements StateChangesInternal {
        static final RenameStateChanges INSTANCE = new RenameStateChanges();

        @Override
        public boolean isApplicable(PsiElement declaration) {
            return PySuggestedRefactoringSupport.Helper.isAvailableForRename(declaration);
        }

        @Override
        public SuggestedRefactoringSupport.@Nullable Signature signature(
            PsiElement declaration,
            @Nullable SuggestedRefactoringState prevState
        ) {
            String name = ((PsiNameIdentifierOwner) declaration).getName();
            if (name == null) {
                return null;
            }
            return SuggestedRefactoringSupport.Signature.create(name, null, List.of(), null);
        }

        @Override
        public List<@Nullable TextRange> parameterMarkerRanges(PsiElement declaration) {
            return List.of();
        }
    }
}
