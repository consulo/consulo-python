/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.parameterInfo.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.xml.XmlStringUtil;
import jakarta.annotation.Nonnull;

import java.util.*;

import static com.jetbrains.python.psi.PyCallExpression.PyMarkedCallee;

/**
 * @author dcheryasov
 */
@ExtensionImpl
public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyCallExpression.PyArgumentsMapping> {

  @Override
  public boolean couldShowInLookup() {
    return true;
  }

  @Override
  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PyArgumentList findElementForParameterInfo(@Nonnull final CreateParameterInfoContext context) {
    PyArgumentList argumentList = findArgumentList(context, -1);
    if (argumentList != null) {
      final PyCallExpression callExpr = argumentList.getCallExpression();
      if (callExpr != null) {
        final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(argumentList.getProject(), argumentList.getContainingFile());
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withRemote().withTypeEvalContext(typeEvalContext);
        final PyCallExpression.PyArgumentsMapping mapping = callExpr.mapArguments(resolveContext);
        if (mapping.getMarkedCallee() != null) {
          context.setItemsToShow(new Object[]{mapping});
          return argumentList;
        }
      }
    }
    return null;
  }

  private static PyArgumentList findArgumentList(final ParameterInfoContext context, int parameterListStart) {
    final int offset = context.getOffset();
    PyArgumentList argumentList = ParameterInfoUtils.findParentOfType(context.getFile(), offset - 1, PyArgumentList.class);
    if (argumentList != null) {
      final TextRange range = argumentList.getTextRange();
      if (parameterListStart >= 0 && range.getStartOffset() != parameterListStart) {
        argumentList = PsiTreeUtil.getParentOfType(argumentList, PyArgumentList.class);
      }
    }
    return argumentList;
  }

  @Override
  public void showParameterInfo(@Nonnull final PyArgumentList element, @Nonnull final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextOffset(), this);
  }

  @Override
  public PyArgumentList findElementForUpdatingParameterInfo(@Nonnull final UpdateParameterInfoContext context) {
    return findArgumentList(context, context.getParameterListStart());
  }

  /**
   * <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
   * We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
   * This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
   */
  @Override
  @RequiredReadAction
  public void updateParameterInfo(@Nonnull final PyArgumentList argumentList, @Nonnull final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() != argumentList) {
      context.removeHint();
      return;
    }
    // align offset to nearest expression; context may point to a space, etc.
    List<PyExpression> flat_args = PyUtil.flattenedParensAndLists(argumentList.getArguments());
    int alleged_cursor_offset = context.getOffset(); // this is already shifted backwards to skip spaces

    final TextRange argListTextRange = argumentList.getTextRange();
    if (!argListTextRange.contains(alleged_cursor_offset) && argumentList.getText().endsWith(")")) {
      context.removeHint();
      return;
    }
    PsiFile file = context.getFile();
    CharSequence chars = file.getViewProvider().getContents();
    int offset = -1;
    for (PyExpression arg : flat_args) {
      TextRange range = arg.getTextRange();
      // widen the range to include all whitespace around the arg
      int left = CharArrayUtil.shiftBackward(chars, range.getStartOffset() - 1, " \t\r\n");
      int right = CharArrayUtil.shiftForwardCarefully(chars, range.getEndOffset(), " \t\r\n");
      if (arg.getParent() instanceof PyListLiteralExpression || arg.getParent() instanceof PyTupleExpression) {
        right = CharArrayUtil.shiftForward(chars, range.getEndOffset(), " \t\r\n])");
      }

      if (left <= alleged_cursor_offset && right >= alleged_cursor_offset) {
        offset = range.getStartOffset();
        break;
      }
    }
    context.setCurrentParameter(offset);
  }

  @Override
  public String getParameterCloseChars() {
    return ",()"; // lpar may mean a nested tuple param, so it's included
  }

  @Override
  public boolean tracksParameterIndex() {
    return false;
  }

  @Override
  public void updateUI(final PyCallExpression.PyArgumentsMapping oldMapping, @Nonnull final ParameterInfoUIContext context) {
    if (oldMapping == null) {
      return;
    }
    final PyCallExpression callExpression = oldMapping.getCallExpression();
    PyPsiUtils.assertValid(callExpression);
    // really we need to redo analysis every UI update; findElementForParameterInfo isn't called while typing
    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(callExpression.getProject(), callExpression.getContainingFile());
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withRemote().withTypeEvalContext(typeEvalContext);
    final PyCallExpression.PyArgumentsMapping mapping = callExpression.mapArguments(resolveContext);
    final PyMarkedCallee marked = mapping.getMarkedCallee();
    if (marked == null) {
      return; // resolution failed
    }
    final PyCallable callable = marked.getCallable();

    final List<PyParameter> parameterList = PyUtil.getParameters(callable, typeEvalContext);
    final List<PyNamedParameter> namedParameters = new ArrayList<>(parameterList.size());

    // param -> hint index. indexes are not contiguous, because some hints are parentheses.
    final Map<PyNamedParameter, Integer> parameterToIndex = new HashMap<>();
    // formatting of hints: hint index -> flags. this includes flags for parens.
    final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags = new HashMap<>();

    final List<String> hintsList = buildParameterListHint(parameterList, namedParameters, parameterToIndex, hintFlags);

    final int currentParamOffset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!

    // gray out enough first parameters as implicit (self, cls, ...)
    for (int i = 0; i < marked.getImplicitOffset(); i += 1) {
      hintFlags.get(parameterToIndex.get(namedParameters.get(i))).add(ParameterInfoUIContextEx.Flag.DISABLE); // show but mark as absent
    }

    final List<PyExpression> flattenedArgs = PyUtil.flattenedParensAndLists(callExpression.getArguments());
    int lastParamIndex = collectHighlights(mapping, parameterList, parameterToIndex, hintFlags, flattenedArgs, currentParamOffset);

    highlightNext(marked, parameterList, namedParameters, parameterToIndex, hintFlags, flattenedArgs.isEmpty(), lastParamIndex);

    String[] hints = ArrayUtil.toStringArray(hintsList);
    if (context instanceof ParameterInfoUIContextEx pic) {
      EnumSet[] flags = new EnumSet[hintFlags.size()];
      for (int i = 0; i < flags.length; i += 1) {
        flags[i] = hintFlags.get(i);
      }
      if (hints.length < 1) {
        hints = new String[]{CodeInsightLocalize.parameterInfoNoParameters().get()};
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }

      //noinspection unchecked
      pic.setupUIComponentPresentation(hints, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no highlight
      StringBuilder signatureBuilder = new StringBuilder();
      if (hints.length > 1) {
        for (String s : hints) {
          signatureBuilder.append(s);
        }
      }
      else {
        signatureBuilder.append(XmlStringUtil.escapeString(CodeInsightLocalize.parameterInfoNoParameters().get()));
      }
      context.setupUIComponentPresentation(signatureBuilder.toString(), -1, 0, false, false, false, context.getDefaultParameterColor());
    }
  }

  @RequiredReadAction
  private static void highlightNext(@Nonnull final PyMarkedCallee marked,
                                    @Nonnull final List<PyParameter> parameterList,
                                    @Nonnull final List<PyNamedParameter> namedParameters,
                                    @Nonnull final Map<PyNamedParameter, Integer> parameterToIndex,
                                    @Nonnull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                    boolean isArgsEmpty,
                                    int lastParamIndex) {
    boolean canOfferNext = true; // can we highlight next unfilled parameter
    for (EnumSet<ParameterInfoUIContextEx.Flag> set : hintFlags.values()) {
      if (set.contains(ParameterInfoUIContextEx.Flag.HIGHLIGHT)) {
        canOfferNext = false;
      }
    }
    // highlight the next parameter to be filled
    if (canOfferNext) {
      int highlightIndex = Integer.MAX_VALUE; // initially beyond reason = no highlight
      if (isArgsEmpty) {
        highlightIndex = marked.getImplicitOffset(); // no args, highlight first (PY-3690)
      }
      else if (lastParamIndex < parameterList.size() - 1) { // lastParamIndex not at end, or no args
        if (namedParameters.get(lastParamIndex).isPositionalContainer()) {
          highlightIndex = lastParamIndex; // stick to *arg
        }
        else {
          highlightIndex = lastParamIndex + 1; // highlight next
        }
      }
      else if (lastParamIndex == parameterList.size() - 1) { // we're right after the end of param list
        if (namedParameters.get(lastParamIndex).isPositionalContainer() || namedParameters.get(lastParamIndex).isKeywordContainer()) {
          highlightIndex = lastParamIndex; // stick to *arg
        }
      }
      if (highlightIndex < namedParameters.size()) {
        hintFlags.get(parameterToIndex.get(namedParameters.get(highlightIndex))).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
      }
    }
  }

  /**
   * match params to available args, highlight current param(s)
   *
   * @return index of last parameter
   */
  @RequiredReadAction
  private static int collectHighlights(@Nonnull final PyCallExpression.PyArgumentsMapping mapping,
                                       @Nonnull final List<PyParameter> parameterList,
                                       @Nonnull final Map<PyNamedParameter, Integer> parameterToIndex,
                                       @Nonnull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                       @Nonnull final List<PyExpression> flatArgs,
                                       int currentParamOffset) {
    final PyMarkedCallee callee = mapping.getMarkedCallee();
    assert callee != null;
    int lastParamIndex = callee.getImplicitOffset();
    final Map<PyExpression, PyNamedParameter> mappedParameters = mapping.getMappedParameters();
    final Map<PyExpression, PyTupleParameter> mappedTupleParameters = mapping.getMappedTupleParameters();
    for (PyExpression arg : flatArgs) {
      final boolean mustHighlight = arg.getTextRange().contains(currentParamOffset);
      PsiElement seeker = arg;
      // An argument tuple may have been flattened; find it
      while (!(seeker instanceof PyArgumentList) && seeker instanceof PyExpression && !mappedParameters.containsKey(seeker)) {
        seeker = seeker.getParent();
      }
      if (seeker instanceof PyExpression) {
        final PyNamedParameter parameter = mappedParameters.get((PyExpression)seeker);
        lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
        if (parameter != null) {
          highlightParameter(parameter, parameterToIndex, hintFlags, mustHighlight);
        }
      }
      else if (PyCallExpressionHelper.isVariadicPositionalArgument(arg)) {
        for (PyNamedParameter parameter : mapping.getParametersMappedToVariadicPositionalArguments()) {
          lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
          highlightParameter(parameter, parameterToIndex, hintFlags, mustHighlight);
        }
      }
      else if (PyCallExpressionHelper.isVariadicKeywordArgument(arg)) {
        for (PyNamedParameter parameter : mapping.getParametersMappedToVariadicKeywordArguments()) {
          lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
          highlightParameter(parameter, parameterToIndex, hintFlags, mustHighlight);
        }
      }
      else {
        final PyTupleParameter tupleParameter = mappedTupleParameters.get(arg);
        if (tupleParameter != null) {
          for (PyNamedParameter parameter : getFlattenedTupleParameterComponents(tupleParameter)) {
            lastParamIndex = Math.max(lastParamIndex, parameterList.indexOf(parameter));
            highlightParameter(parameter, parameterToIndex, hintFlags, mustHighlight);
          }
        }
      }
    }
    return lastParamIndex;
  }

  @Nonnull
  private static List<PyNamedParameter> getFlattenedTupleParameterComponents(@Nonnull PyTupleParameter parameter) {
    final List<PyNamedParameter> results = new ArrayList<>();
    for (PyParameter component : parameter.getContents()) {
      if (component instanceof PyNamedParameter) {
        results.add((PyNamedParameter)component);
      }
      else if (component instanceof PyTupleParameter) {
        results.addAll(getFlattenedTupleParameterComponents((PyTupleParameter)component));
      }
    }
    return results;
  }

  private static void highlightParameter(@Nonnull final PyNamedParameter parameter,
                                         @Nonnull final Map<PyNamedParameter, Integer> parameterToIndex,
                                         @Nonnull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags,
                                         boolean mustHighlight) {
    final Integer parameterIndex = parameterToIndex.get(parameter);
    if (mustHighlight && parameterIndex != null && parameterIndex < hintFlags.size()) {
      hintFlags.get(parameterIndex).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
    }
  }

  /**
   * builds the textual picture and the list of named parameters
   *
   * @param parameters       parameters of a callable
   * @param namedParameters  used to collect all named parameters of callable
   * @param parameterToIndex used to collect info about parameter indexes
   * @param hintFlags        mark parameter as deprecated/highlighted/strikeout
   */
  private static List<String> buildParameterListHint(@Nonnull List<PyParameter> parameters,
                                                     @Nonnull final List<PyNamedParameter> namedParameters,
                                                     @Nonnull final Map<PyNamedParameter, Integer> parameterToIndex,
                                                     @Nonnull final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hintFlags) {
    final List<String> hintsList = new ArrayList<>();
    ParamHelper.walkDownParamArray(parameters.toArray(new PyParameter[parameters.size()]), new ParamHelper.ParamWalker() {
      @Override
      public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
        hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
        hintsList.add("(");
      }

      @Override
      public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
        hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
        hintsList.add(last ? ")" : "), ");
      }

      @Override
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        namedParameters.add(param);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(param.getRepr(true));
        if (!last) {
          stringBuilder.append(", ");
        }
        int hintIndex = hintsList.size();
        parameterToIndex.put(param, hintIndex);
        hintFlags.put(hintIndex, EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
        hintsList.add(stringBuilder.toString());
      }

      @Override
      public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
        hintFlags.put(hintsList.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
        hintsList.add(last ? "*" : "*, ");
      }
    });
    return hintsList;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
