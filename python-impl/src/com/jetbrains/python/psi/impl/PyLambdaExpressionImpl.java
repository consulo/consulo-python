package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
  public PyLambdaExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType type = provider.getCallableType(this, context);
      if (type != null) {
        return type;
      }
    }
    return new PyFunctionType(this);
  }

  @NotNull
  public PyParameterList getParameterList() {
    final PyElement child = childToPsi(PyElementTypes.PARAMETER_LIST_SET, 0);
    if (child == null) {
      throw new RuntimeException("parameter list must not be null; text=" + getText());
    }
    //noinspection unchecked
    return (PyParameterList)child;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    final PyExpression body = getBody();
    if (body != null) return context.getType(body);
    else return null;
  }

  @Nullable
  public PyExpression getBody() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyFunction asMethod() {
    return null; // we're never a method
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return null;
  }
}
