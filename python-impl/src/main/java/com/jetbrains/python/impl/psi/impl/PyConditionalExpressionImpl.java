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

import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.List;

/**
 * @author yole
 */
public class PyConditionalExpressionImpl extends PyElementImpl implements PyConditionalExpression {
    public PyConditionalExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    @RequiredReadAction
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression truePart = getTruePart();
        PyExpression falsePart = getFalsePart();
        if (truePart == null || falsePart == null) {
            return null;
        }
        return PyUnionType.union(context.getType(truePart), context.getType(falsePart));
    }

    @Override
    @RequiredReadAction
    public PyExpression getTruePart() {
        List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
        return expressions.get(0);
    }

    @Override
    @RequiredReadAction
    public PyExpression getCondition() {
        List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
        return expressions.size() > 1 ? expressions.get(1) : null;
    }

    @Override
    @RequiredReadAction
    public PyExpression getFalsePart() {
        List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
        return expressions.size() == 3 ? expressions.get(2) : null;
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyConditionalExpression(this);
    }
}
