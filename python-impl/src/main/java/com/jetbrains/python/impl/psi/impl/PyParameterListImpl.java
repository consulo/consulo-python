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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class PyParameterListImpl extends PyBaseElementImpl<PyParameterListStub> implements PyParameterList {
    public PyParameterListImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyParameterListImpl(PyParameterListStub stub) {
        this(stub, PyElementTypes.PARAMETER_LIST);
    }

    public PyParameterListImpl(PyParameterListStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyParameterList(this);
    }

    @Override
    @RequiredReadAction
    public PyParameter[] getParameters() {
        return getStubOrPsiChildren(PythonDialectsTokenSetProvider.INSTANCE.getParameterTokens(), new PyParameter[0]);
    }

    @Override
    public void addParameter(PyNamedParameter param) {
        PsiElement paren = getLastChild();
        if (paren != null && ")".equals(paren.getText())) {
            ASTNode beforeWhat = paren.getNode(); // the closing paren will be this
            PyParameter[] params = getParameters();
            boolean hasDefaultValue = param.hasDefaultValue();
            boolean isLast = true;
            for (PyParameter p : params) {
                if (!hasDefaultValue && p.hasDefaultValue()) {
                    beforeWhat = p.getNode();
                    isLast = false;
                    break;
                }
                if (p instanceof PyNamedParameter named) {
                    if (named.isKeywordContainer() || named.isPositionalContainer()) {
                        beforeWhat = p.getNode();
                        isLast = false;
                        break;
                    }
                }
            }
            ASTNode previous = PyPsiUtils.getPrevNonWhitespaceSibling(beforeWhat);
            PyUtil.addListNode(
                this,
                param,
                beforeWhat,
                !isLast || params.length == 0 || previous.getElementType() == PyTokenTypes.COMMA,
                isLast,
                beforeWhat.getElementType() != PyTokenTypes.RPAR
            );
        }
    }

    @Override
    @RequiredReadAction
    public boolean hasPositionalContainer() {
        for (PyParameter parameter : getParameters()) {
            if (parameter instanceof PyNamedParameter named && named.isPositionalContainer()) {
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiredReadAction
    public boolean hasKeywordContainer() {
        for (PyParameter parameter : getParameters()) {
            if (parameter instanceof PyNamedParameter named && named.isKeywordContainer()) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    @RequiredReadAction
    public PyNamedParameter findParameterByName(final String name) {
        SimpleReference<PyNamedParameter> result = new SimpleReference<>();
        ParamHelper.walkDownParamArray(
            getParameters(),
            new ParamHelper.ParamVisitor() {
                @Override
                public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                    if (name.equals(param.getName())) {
                        result.set(param);
                    }
                }
            }
        );
        return result.get();
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getPresentableText(final boolean includeDefaultValue) {
        final StringBuilder target = new StringBuilder();
        final String COMMA = ", ";
        target.append("(");
        ParamHelper.walkDownParamArray(getParameters(), new ParamHelper.ParamWalker() {
            @Override
            public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
                target.append("(");
            }

            @Override
            public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
                target.append(")");
                if (!last) {
                    target.append(COMMA);
                }
            }

            @Override
            public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                target.append(param.getRepr(includeDefaultValue));
                if (!last) {
                    target.append(COMMA);
                }
            }

            @Override
            public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
                target.append('*');
                if (!last) {
                    target.append(COMMA);
                }
            }
        });
        target.append(")");
        return LocalizeValue.of(target.toString());
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyFunction getContainingFunction() {
        return getParentByStub() instanceof PyFunction f ? f : null;
    }

    @Override
    @RequiredWriteAction
    public void deleteChildInternal(ASTNode node) {
        if (ArrayUtil.contains(node.getPsi(), getParameters())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, node.getPsi());
        }
        super.deleteChildInternal(node);
    }
}
