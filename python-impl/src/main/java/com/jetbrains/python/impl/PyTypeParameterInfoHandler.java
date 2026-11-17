// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.parameterInfo.PyTypeParameterInfoUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.parameterInfo.CreateParameterInfoContext;
import consulo.language.editor.parameterInfo.ParameterInfoContext;
import consulo.language.editor.parameterInfo.ParameterInfoHandler;
import consulo.language.editor.parameterInfo.ParameterInfoUIContext;
import consulo.language.editor.parameterInfo.SignatureBuilder;
import consulo.language.editor.parameterInfo.SignatureStyle;
import consulo.language.editor.parameterInfo.UpdateParameterInfoContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Shows the "Parameter Info" popup with the expected type parameters when the caret is inside the square brackets of a
 * parameterized type, e.g. {@code Generator[<caret>]} displays {@code _YieldT_co, _SendT_contra, _ReturnT_co}.
 * <p>
 * This is a separate handler from {@link PyParameterInfoHandler} (which handles call argument lists) and is registered with a
 * higher priority so that a generic subscription nested in a call, such as {@code cast(list[<caret>], x)}, shows the type
 * parameters of the subscription rather than the parameters of the enclosing call.
 */
@ExtensionImpl(order = "before pyParameterInfoHandler")
public class PyTypeParameterInfoHandler implements ParameterInfoHandler<PySubscriptionExpression, PyTypeParameterInfoHandler.TypeParameterListPresentation> {

    public static class TypeParameterListPresentation {
        private final List<String> myRepresentations;

        public TypeParameterListPresentation(List<String> representations) {
            myRepresentations = representations;
        }

        public List<String> getRepresentations() {
            return myRepresentations;
        }
    }

    // Consulo: ParameterInfoHandler requires lookup support methods
    @Override
    public boolean couldShowInLookup() {
        return false;
    }

    @Override
    public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    @RequiredReadAction
    public @Nullable PySubscriptionExpression findElementForParameterInfo(CreateParameterInfoContext context) {
        PySubscriptionExpression subscription = findTypeParameterList(context.getFile(), context.getOffset());
        if (subscription == null) {
            return null;
        }
        TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(context.getProject(), context.getFile());
        List<String> representations = PyTypeParameterInfoUtil.getTypeParameterRepresentations(subscription, typeEvalContext);
        if (representations.isEmpty()) {
            return null;
        }
        context.setItemsToShow(new Object[]{new TypeParameterListPresentation(representations)});
        return subscription;
    }

    @Override
    @RequiredReadAction
    public void showParameterInfo(PySubscriptionExpression element, CreateParameterInfoContext context) {
        context.showHint(element, element.getTextRange().getStartOffset(), this);
    }

    @Override
    @RequiredReadAction
    public @Nullable PySubscriptionExpression findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
        return findTypeParameterList(context.getFile(), context.getOffset());
    }

    @Override
    @RequiredReadAction
    public void updateParameterInfo(PySubscriptionExpression parameterOwner, UpdateParameterInfoContext context) {
        context.setCurrentParameter(currentTypeParameterIndex(parameterOwner, context.getOffset()));
    }

    @Override
    public void updateUI(TypeParameterListPresentation p, ParameterInfoUIContext context) {
        List<String> representations = p.getRepresentations();
        int size = representations.size();
        if (size == 0) {
            return;
        }
        int highlighted = highlightedIndex(context.getCurrentParameterIndex(), representations);

        // Consulo: ParameterInfoUIContextEx#setupUIComponentPresentation(String[], EnumSet[], Color) is expressed with SignatureBuilder
        SignatureBuilder signature = context.signature();
        for (int i = 0; i < size; i++) {
            if (i == highlighted) {
                signature.parameter(representations.get(i), SignatureStyle.HIGHLIGHT);
            }
            else {
                signature.parameter(representations.get(i));
            }
            if (i < size - 1) {
                signature.comma();
            }
        }
        signature.apply();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @RequiredReadAction
    private static @Nullable PySubscriptionExpression findTypeParameterList(PsiFile file, int offset) {
        PsiElement element = PsiUtilCore.getElementAtOffset(file, offset > 0 ? offset - 1 : offset);
        PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression.class, false);
        if (subscription == null) {
            return null;
        }
        // The caret has to be inside the square brackets, not on the operand or past the closing bracket.
        ASTNode leftBracket = subscription.getNode().findChildByType(PyTokenTypes.LBRACKET);
        if (leftBracket == null) {
            return null;
        }
        if (offset <= leftBracket.getStartOffset()) {
            return null;
        }
        ASTNode rightBracket = subscription.getNode().findChildByType(PyTokenTypes.RBRACKET);
        if (rightBracket != null && offset > rightBracket.getStartOffset()) {
            return null;
        }
        return subscription;
    }

    @RequiredReadAction
    private static int currentTypeParameterIndex(PySubscriptionExpression subscription, int offset) {
        // A single type argument has no comma, so it always maps to the first type parameter.
        PyExpression indexExpression = subscription.getIndexExpression();
        if (!(indexExpression instanceof PyTupleExpression index)) {
            return 0;
        }
        int slot = 0;
        ASTNode child = index.getNode().getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == PyTokenTypes.COMMA && child.getStartOffset() < offset) {
                slot++;
            }
            child = child.getTreeNext();
        }
        return slot;
    }

    private static int highlightedIndex(int current, List<String> representations) {
        int size = representations.size();
        if (current >= 0 && current < size) {
            return current;
        }
        // A trailing TypeVarTuple/ParamSpec (rendered with a leading '*') absorbs any extra positional type arguments.
        if (current >= size && !representations.isEmpty() && representations.get(size - 1).startsWith("*")) {
            return size - 1;
        }
        return -1;
    }
}
