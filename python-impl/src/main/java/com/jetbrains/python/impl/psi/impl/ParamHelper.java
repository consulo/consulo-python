/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter-related things that should not belong directly to PyParameter.
 */
public class ParamHelper {

  private ParamHelper() {
  }

  /**
   * Runs a {@link ParamWalker ParamWalker} down the array of parameters, recursively descending into tuple parameters.
   * If the array is from PyParamaterList.getParameters(), parameters are visited in the order of textual appearance
   * @param params where to walk
   * @param walker the walker with callbacks.
   */
  public static void walkDownParamArray(PyParameter[] params, ParamWalker walker) {
    int i = 0;
    for (PyParameter psi : params) {
      final boolean first = i == 0;
      final boolean last = i == params.length - 1;

      if (psi instanceof PyTupleParameter tupleParameter) {
        walker.enterTupleParameter(tupleParameter, first, last);
        walkDownParamArray(tupleParameter.getContents(), walker);
        walker.leaveTupleParameter(tupleParameter, first, last);
      }
      else if (psi instanceof PyNamedParameter) {
        walker.visitNamedParameter((PyNamedParameter)psi, first, last);
      }
      else if (psi instanceof PySlashParameter) {
        walker.visitSlashParameter((PySlashParameter)psi, first, last);
      }
      else if (psi instanceof PySingleStarParameter) {
        walker.visitSingleStarParameter((PySingleStarParameter)psi, first, last);
      }
      // TODO consulo: JB walks PyCallableParameter (walkDownParameters) and also visits the non-PSI separators / visitNonPsiParameter; PyCallableParameter is not ported yet
      i++;
    }
  }

  public interface ParamWalker {
    /**
     * Is called when a tuple parameter is encountered, before visiting any parameters nested in it.
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last true it is the last in the list
     */
    void enterTupleParameter(PyTupleParameter param, boolean first, boolean last);

    /**
     * Is called when all nested parameters of a given tuple parameter are visited.
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last true it is the last in the list
     */
    void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last);

    /**
     * Is called when a named parameter is encountered.
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last true it is the last in the list
     */
    void visitNamedParameter(PyNamedParameter param, boolean first, boolean last);

    /**
     * Is called when a positional-only separator ({@code /}) is encountered.
     *
     * @param param the underlying PSI element, or {@code null} if the separator is synthetic
     */
    void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last);

    /**
     * Is called when a keyword-only separator ({@code *}) is encountered.
     *
     * @param param the underlying PSI element, or {@code null} if the separator is synthetic
     */
    void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last);
  }

  public static abstract class ParamVisitor implements ParamWalker {
    @Override
    public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) { }

    @Override
    public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) { }

    @Override
    public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) { }

    @Override
    public void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last) { }

    @Override
    public void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last) { }
  }

  public static List<PyNamedParameter> collectNamedParameters(PyParameterList plist) {
    final List<PyNamedParameter> result = new ArrayList<>(10); // a random 'enough'
    walkDownParamArray(
      plist.getParameters(),
      new ParamVisitor() {
        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          result.add(param);
        }
      }
    );
    return result;
  }
}
