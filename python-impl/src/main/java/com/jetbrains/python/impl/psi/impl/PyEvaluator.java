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

import consulo.language.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

/**
 * @author yole
 */
public class PyEvaluator {
  private Set<PyExpression> myVisited = new HashSet<PyExpression>();
  private Map<String, Object> myNamespace;
  private boolean myEvaluateCollectionItems = true;

  /**
   * if true, references will be resolved
   */
  private boolean myEnableResolve = true;

  public void setNamespace(Map<String, Object> namespace) {
    myNamespace = namespace;
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }

  public void enableResolve(boolean enableResolve) {
    myEnableResolve = enableResolve;
  }

  public Object evaluate(PyExpression expr) {
    if (expr == null || myVisited.contains(expr)) {
      return null;
    }
    myVisited.add(expr);
    if (expr instanceof PyParenthesizedExpression) {
      return evaluate(((PyParenthesizedExpression) expr).getContainedExpression());
    }
    if (expr instanceof PySequenceExpression) {
      PyExpression[] elements = ((PySequenceExpression)expr).getElements();
      if (expr instanceof PyDictLiteralExpression) {
        Map<Object, Object> result = new HashMap<Object, Object>();
        for (PyKeyValueExpression keyValueExpression : ((PyDictLiteralExpression)expr).getElements()) {
          Object dictKey = evaluate(keyValueExpression.getKey());
          if (dictKey != null) {
            PyExpression value = keyValueExpression.getValue();
            result.put(dictKey, myEvaluateCollectionItems ? evaluate(value) : value);
          }
        }
        return result;
      }
      else {
        List<Object> result = new ArrayList<Object>();
        for (PyExpression element : elements) {
          result.add(myEvaluateCollectionItems ? evaluate(element) : element);
        }
        return result;
      }
    }
    if (expr instanceof PyCallExpression) {
      return evaluateCall((PyCallExpression)expr);
    }
    else if (expr instanceof PyReferenceExpression) {
      return evaluateReferenceExpression((PyReferenceExpression)expr);
    }
    else if (expr instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expr).getStringValue();
    }
    else if (expr instanceof PyNumericLiteralExpression numericLiteralExpression) {
      return evaluateNumeric(numericLiteralExpression);
    }
    else if (expr instanceof PyBinaryExpression) {
      PyBinaryExpression binaryExpr = (PyBinaryExpression)expr;
      PyElementType op = binaryExpr.getOperator();
      if (op == PyTokenTypes.PLUS) {
        Object lhs = evaluate(binaryExpr.getLeftExpression());
        Object rhs = evaluate(binaryExpr.getRightExpression());
        if (lhs != null && rhs != null) {
          return concatenate(lhs, rhs);
        }
      }
    }
    return null;
  }

  public static Object concatenate(Object lhs, Object rhs) {
    if (lhs instanceof String && rhs instanceof String) {
      return (String) lhs + (String) rhs;
    }
    if (lhs instanceof List && rhs instanceof List) {
      List<Object> result = new ArrayList<Object>();
      result.addAll((List) lhs);
      result.addAll((List) rhs);
      return result;
    }
    return null;
  }

  protected Object evaluateReferenceExpression(PyReferenceExpression expr) {
    if (expr.getQualifier() == null) {
      if (myNamespace != null) {
        return myNamespace.get(expr.getReferencedName());
      }
      if (!myEnableResolve) {
        return null;
      }
      PsiElement result = expr.getReference(PyResolveContext.noImplicits()).resolve();
      if (result instanceof PyTargetExpression) {
        result = ((PyTargetExpression)result).findAssignedValue();
      }
      if (result instanceof PyExpression) {
        return evaluate((PyExpression)result);
      }
    }
    return null;
  }

  protected Object evaluateCall(PyCallExpression call) {
    PyExpression[] args = call.getArguments();
    if (call.isCalleeText(PyNames.REPLACE) && args.length == 2) {
      PyExpression callee = call.getCallee();
      if (!(callee instanceof PyQualifiedExpression)) return null;
      PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
      Object result = evaluate(qualifier);
      if (result instanceof String) {
        Object arg1 = evaluate(args[0]);
        Object arg2 = evaluate(args[1]);
        if (arg1 instanceof String && arg2 instanceof String) {
          return ((String) result).replace((String) arg1, (String) arg2);
        }
      }
    }
    return null;
  }

  private static final QualifiedName SYS_VERSION_INFO = QualifiedName.fromDottedString("sys.version_info");

  private static @Nullable Object evaluateNumeric(PyNumericLiteralExpression expression) {
    if (expression.isIntegerLiteral()) {
      return fromBigInteger(expression.getBigIntegerValue());
    }
    return null;
  }

  public static int @Nullable [] evaluateAsVersion(@Nullable PyExpression expression) {
    if (!(PyPsiUtils.flattenParens(expression) instanceof PyTupleExpression tupleExpression)) {
      return null;
    }
    PyExpression[] elements = tupleExpression.getElements();
    int[] result = new int[elements.length];
    for (int i = 0; i < elements.length; i++) {
      Integer value = evaluateNoResolve(elements[i], Integer.class);
      if (value == null) {
        return null;
      }
      result[i] = value;
    }
    return result;
  }

  private static Number fromBigInteger(BigInteger value) {
    int intValue = value.intValue();
    if (BigInteger.valueOf(intValue).equals(value)) {
      return intValue;
    }

    long longValue = value.longValue();
    if (BigInteger.valueOf(longValue).equals(value)) {
      return longValue;
    }

    return value;
  }

  /**
   * Shortcut that evaluates expression with default params and disabled resolve and casts it to particular type (if possible)
   *
   * @param <T>        expected type
   * @param expression expression to evaluate
   * @param resultType expected type
   * @return value if expression is evaluated to this type, null otherwise
   */
  public static <T> @Nullable T evaluateNoResolve(@Nullable PyExpression expression, Class<T> resultType) {
    PyEvaluator evaluator = new PyEvaluator();
    evaluator.enableResolve(false);
    return PyUtil.as(evaluator.evaluate(expression), resultType);
  }

  public static boolean isSysVersionInfoExpression(@Nullable PyExpression expression) {
    if (!(PyPsiUtils.flattenParens(expression) instanceof PyReferenceExpression referenceExpression)) {
      return false;
    }
    return SYS_VERSION_INFO.equals(referenceExpression.asQualifiedName());
  }
}
