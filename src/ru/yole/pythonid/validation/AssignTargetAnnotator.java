/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import ru.yole.pythonid.psi.PyAssignmentStatement;
import ru.yole.pythonid.psi.PyAugAssignmentStatement;
import ru.yole.pythonid.psi.PyBinaryExpression;
import ru.yole.pythonid.psi.PyCallExpression;
import ru.yole.pythonid.psi.PyDelStatement;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExceptBlock;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyForStatement;
import ru.yole.pythonid.psi.PyGeneratorExpression;
import ru.yole.pythonid.psi.PyLambdaExpression;
import ru.yole.pythonid.psi.PyListCompExpression;
import ru.yole.pythonid.psi.PyListLiteralExpression;
import ru.yole.pythonid.psi.PyNumericLiteralExpression;
import ru.yole.pythonid.psi.PyParenthesizedExpression;
import ru.yole.pythonid.psi.PyReferenceExpression;
import ru.yole.pythonid.psi.PyStringLiteralExpression;
import ru.yole.pythonid.psi.PyTargetExpression;
import ru.yole.pythonid.psi.PyTupleExpression;

public class AssignTargetAnnotator extends PyAnnotator
{
  public void visitPyAssignmentStatement(PyAssignmentStatement node)
  {
    ExprVisitor visitor = new ExprVisitor(Operation.Assign);
    for (PyExpression expr : node.getTargets())
      expr.accept(visitor);
  }

  public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node)
  {
    node.getTarget().accept(new ExprVisitor(Operation.AugAssign));
  }

  public void visitPyDelStatement(PyDelStatement node) {
    ExprVisitor visitor = new ExprVisitor(Operation.Delete);
    for (PyExpression expr : node.getTargets())
      expr.accept(visitor);
  }

  public void visitPyExceptBlock(PyExceptBlock node)
  {
    PyExpression target = node.getTarget();
    if (target != null)
      target.accept(new ExprVisitor(Operation.Except));
  }

  public void visitPyForStatement(PyForStatement node)
  {
    PyExpression target = node.getTargetExpression();
    if (target != null)
      target.accept(new ExprVisitor(Operation.For));
  }

  private class ExprVisitor extends PyElementVisitor
  {
    private AssignTargetAnnotator.Operation _op;

    public ExprVisitor(AssignTargetAnnotator.Operation op) {
      this._op = op;
    }

    public void visitPyReferenceExpression(PyReferenceExpression node) {
      String referencedName = node.getReferencedName();
      if ((referencedName != null) && (referencedName.equals("None")))
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, this._op == AssignTargetAnnotator.Operation.Delete ? "deleting None" : "assignment to None");
    }

    public void visitPyTargetExpression(PyTargetExpression node)
    {
      String targetName = node.getName();
      if ((targetName != null) && (targetName.equals("None")))
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, this._op == AssignTargetAnnotator.Operation.Delete ? "deleting None" : "assignment to None");
    }

    public void visitPyCallExpression(PyCallExpression node)
    {
      AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, this._op == AssignTargetAnnotator.Operation.Delete ? "can't delete function call" : "can't assign to function call");
    }

    public void visitPyGeneratorExpression(PyGeneratorExpression node)
    {
      AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, this._op == AssignTargetAnnotator.Operation.AugAssign ? "augmented assign to generator expression not possible" : "assign to generator expression not possible");
    }

    public void visitPyBinaryExpression(PyBinaryExpression node)
    {
      AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "can't assign to operator");
    }

    public void visitPyTupleExpression(PyTupleExpression node) {
      if (node.getElements().length == 0) {
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "can't assign to ()");
      }
      else if (this._op == AssignTargetAnnotator.Operation.AugAssign) {
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "augmented assign to tuple literal or generator expression not possible");
      }
      else
        node.acceptChildren(this);
    }

    public void visitPyParenthesizedExpression(PyParenthesizedExpression node)
    {
      if (this._op == AssignTargetAnnotator.Operation.AugAssign) {
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "augmented assign to tuple literal or generator expression not possible");
      }
      else
        node.acceptChildren(this);
    }

    public void visitPyListLiteralExpression(PyListLiteralExpression node)
    {
      if (node.getElements().length == 0) {
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "can't assign to []");
      }
      else if (this._op == AssignTargetAnnotator.Operation.AugAssign) {
        AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "augmented assign to list literal or comprehension not possible");
      }
      else
        node.acceptChildren(this);
    }

    public void visitPyListCompExpression(PyListCompExpression node)
    {
      AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, this._op == AssignTargetAnnotator.Operation.AugAssign ? "augmented assign to list comprehension not possible" : "can't assign to list comprehension");
    }

    public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node)
    {
      checkLiteral(node);
    }

    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      checkLiteral(node);
    }

    private void checkLiteral(PyExpression node) {
      AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "can't assign to literal");
    }

    public void visitPyLambdaExpression(PyLambdaExpression node) {
      AssignTargetAnnotator.this.getHolder().createErrorAnnotation(node, "can't assign to lambda");
    }
  }

  private static enum Operation
  {
    Assign, AugAssign, Delete, Except, For;
  }
}