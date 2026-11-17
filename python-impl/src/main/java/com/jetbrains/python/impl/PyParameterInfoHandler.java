// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.parameterInfo.ParameterHints;
import com.jetbrains.python.impl.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.registry.Registry;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.parameterInfo.CreateParameterInfoContext;
import consulo.language.editor.parameterInfo.DeleteParameterInfoContext;
import consulo.language.editor.parameterInfo.ParameterInfoContext;
import consulo.language.editor.parameterInfo.ParameterInfoHandler;
import consulo.language.editor.parameterInfo.ParameterInfoUIContext;
import consulo.language.editor.parameterInfo.SignatureBuilder;
import consulo.language.editor.parameterInfo.SignatureStyle;
import consulo.language.editor.parameterInfo.UpdateParameterInfoContext;
import consulo.language.psi.PsiFile;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@ExtensionImpl(id = "pyParameterInfoHandler")
public final class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyParameterInfoUtils.CallInfo> {
    private static final int MY_PARAM_LENGTH_LIMIT = 50;
    private static final int MAX_PARAMETER_INFO_TO_SHOW = 20;

    private boolean hideOverloads = true;
    private boolean isDisposed = false;
    private int myRealOffset = -1;
    private int numOfSignatures = 0;
    private @Nullable CreateParameterInfoContext myCreateContext;

    @Override
    public boolean couldShowInLookup() {
        return true;
    }

    @Override
    public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public @Nullable PyArgumentList findElementForParameterInfo(CreateParameterInfoContext context) {
        PsiFile file = context.getFile();
        int offset = context.getOffset();
        final PyArgumentList argumentList = PyParameterInfoUtils.findArgumentList(file, offset, -1);

        List<Pair<PyCallExpression, PyCallableType>> parameterInfos = PyParameterInfoUtils.findCallCandidates(argumentList);
        if (parameterInfos != null) {
            if (parameterInfos.size() > MAX_PARAMETER_INFO_TO_SHOW) {
                parameterInfos = parameterInfos.subList(0, MAX_PARAMETER_INFO_TO_SHOW);
            }
            List<PyParameterInfoUtils.CallInfo> infos = new ArrayList<>();

            boolean isFirst = true;
            for (Pair<PyCallExpression, PyCallableType> paramInfo : parameterInfos) {
                infos.add(new PyParameterInfoUtils.CallInfo(paramInfo.first, paramInfo.second, isFirst || hideOverloads));
                isFirst = false;
            }

            Object[] infoArr = infos.toArray();
            setDisplayAllOverloadsState(infoArr, hideOverloads);
            context.setItemsToShow(infoArr);
            numOfSignatures = getNumOfSignatures(infoArr);
            return argumentList;
        }

        return null;
    }

    @Override
    public void showParameterInfo(PyArgumentList element, CreateParameterInfoContext context) {
        // Show all overloads on second shortcut hit at the same offset
        myCreateContext = context;
        isDisposed = false;
        int actualOffset = getRealCaretOffset(context.getEditor());
        if (actualOffset == myRealOffset) {
            hideOverloads = !hideOverloads;
        }
        context.showHint(element, element.getTextOffset(), this);
    }

    @Override
    public @Nullable PyArgumentList findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
        myRealOffset = getRealCaretOffset(context.getEditor());
        return PyParameterInfoUtils.findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());
    }

    /*
     <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
     We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
     This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
     */
    @Override
    public void updateParameterInfo(PyArgumentList argumentList, UpdateParameterInfoContext context) {
        final int allegedCursorOffset = context.getOffset(); // this is already shifted backwards to skip spaces

        if (!argumentList.getTextRange().contains(allegedCursorOffset) && argumentList.getText().endsWith(")")) {
            context.removeHint();
            return;
        }

        final PsiFile file = context.getFile();
        int offset = PyParameterInfoUtils.findCurrentParameter(argumentList, allegedCursorOffset, file);

        setDisplayAllOverloadsState(context.getObjectsToView(), hideOverloads);

        context.setCurrentParameter(offset);
    }

    @Override
    public void updateUI(PyParameterInfoUtils.CallInfo description, ParameterInfoUIContext context) {
        // TODO consulo: ParameterInfoUIContext.setUIComponentVisible missing
        // context.setUIComponentVisible(description.isVisible());
        final int currentParamOffset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!
        ParameterHints parameterHints = PyParameterInfoUtils.buildParameterHints(description.getCallandCalleePair(), currentParamOffset);
        if (parameterHints == null) {
            return;
        }

        boolean showAllHints = Registry.is("python.parameter.info.show.all.hints", false);
        List<PyParameterInfoUtils.ParameterDescription> parameterDescriptions = parameterHints.getParameterDescriptors();
        String[] hintsToShow = new String[parameterDescriptions.size()];
        //noinspection unchecked
        EnumSet<SignatureStyle>[] flags = new EnumSet[parameterHints.getFlags().size()];
        for (int i = 0; i < flags.length; i++) {
            EnumSet<SignatureStyle> curFlags = parameterHints.getFlags().get(i);
            PyParameterInfoUtils.ParameterDescription representation = parameterDescriptions.get(i);
            hintsToShow[i] = getRepresentationToShow(representation, curFlags.contains(SignatureStyle.HIGHLIGHT), showAllHints);
            flags[i] = EnumSet.copyOf(parameterHints.getFlags().get(i));
        }
        // Consulo: ParameterInfoUIContextEx#setupUIComponentPresentation(String[], EnumSet[], Color) is expressed with SignatureBuilder
        SignatureBuilder signature = context.signature();
        if (parameterDescriptions.isEmpty()) {
            signature.text(getNoParamsMsg(), SignatureStyle.DISABLED);
        }
        else {
            for (int i = 0; i < hintsToShow.length; i++) {
                String hint = hintsToShow[i];
                SignatureStyle[] styles = i < flags.length ? flags[i].toArray(new SignatureStyle[0]) : new SignatureStyle[0];
                boolean comma = hint.endsWith(", ");
                if (comma) {
                    hint = hint.substring(0, hint.length() - 2);
                }
                if (hint.equals("(") || hint.equals(")")) {
                    signature.text(hint, styles);
                }
                else {
                    signature.parameter(hint, styles);
                }
                if (comma) {
                    signature.comma();
                }
            }
        }
        signature.apply();
    }

    // TODO consulo: ParameterInfoHandler.createBottomComponent/updateBottomComponent missing (JB "Show N more overloads" action link)

    private static int getNumOfSignatures(Object[] objectsToShow) {
        if (objectsToShow != null) {
            return objectsToShow.length;
        }
        return 0;
    }

    @Override
    public void dispose(DeleteParameterInfoContext context) {
        resetDisplayState();
        myCreateContext = null;
        isDisposed = true;
        ParameterInfoHandler.super.dispose(context);
    }

    private void resetDisplayState() {
        myRealOffset = -1;
        numOfSignatures = 0;
        hideOverloads = true;
    }

    private static String getRepresentationToShow(PyParameterInfoUtils.ParameterDescription description,
                                                  boolean isHighlighted,
                                                  boolean showHints) {
        String fullRepresentation = description.getFullRepresentation(isHighlighted || showHints);
        if (fullRepresentation.length() > MY_PARAM_LENGTH_LIMIT && !isHighlighted) {
            String annotation = description.getAnnotation();
            if (!annotation.isEmpty() && annotation.length() < fullRepresentation.length()) {
                return annotation;
            }
        }
        return fullRepresentation;
    }

    private static String getNoParamsMsg() {
        return CodeInsightLocalize.parameterInfoNoParameters().get();
    }

    /* ParameterInfoContext.getOffset() does not represent the real offset in cases like
     * foo(a, b,     <caret>) as it skips whitespaces
     * The real offset is required for correct folding and unfolding of the overloads
     * when the shortcut is pressed at the same *real* offset
     */
    private static int getRealCaretOffset(Editor editor) {
        return editor.getCaretModel().getCurrentCaret().getOffset();
    }

    private static void setDisplayAllOverloadsState(Object[] hintsToShow, boolean hideOverloads) {
        if (hintsToShow != null && hintsToShow.length != 0) {
            ((PyParameterInfoUtils.CallInfo)hintsToShow[0]).setVisible(true);
            for (int i = 1; i < hintsToShow.length; i++) {
                ((PyParameterInfoUtils.CallInfo)hintsToShow[i]).setVisible(!hideOverloads);
            }
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
