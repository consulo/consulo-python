// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.ast.ASTNode;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;


// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PyParameterListImpl extends PyBaseElementImpl<PyParameterListStub> implements PyParameterList {
    public PyParameterListImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyParameterListImpl(PyParameterListStub stub) {
        this(stub, PyStubElementTypes.PARAMETER_LIST);
    }

    public PyParameterListImpl(PyParameterListStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyParameterList(this);
    }

    @Override
    public PyParameter[] getParameters() {
        return getStubOrPsiChildren(PythonDialectsTokenSetProvider.getInstance().getParameterTokens(), new PyParameter[0]);
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
            PyUtil.addListNode(this, param, beforeWhat, !isLast || params.length == 0 ||
                                                        previous.getElementType() == PyTokenTypes.COMMA, isLast,
                               beforeWhat.getElementType() != PyTokenTypes.RPAR);
        }
    }

    @Override
    public boolean hasPositionalContainer() {
        for (PyParameter parameter : getParameters()) {
            if (parameter instanceof PyNamedParameter && ((PyNamedParameter)parameter).isPositionalContainer()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasKeywordContainer() {
        for (PyParameter parameter : getParameters()) {
            if (parameter instanceof PyNamedParameter && ((PyNamedParameter)parameter).isKeywordContainer()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable PyNamedParameter findParameterByName(String name) {
        Ref<PyNamedParameter> result = new Ref<>();
        ParamHelper.walkDownParamArray(getParameters(), new ParamHelper.ParamVisitor() {
            @Override
            public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                if (name.equals(param.getName())) {
                    result.set(param);
                }
            }
        });
        return result.get();
    }

    @Override
    public String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context) {
        return ParamHelper.getPresentableText(getParameters(), includeDefaultValue, context);
    }

    @Override
    public @Nullable PyCallable getContainingCallable() {
        PsiElement parent = getParentByStub();
        return parent instanceof PyCallable callable ? callable : null;
    }

    @Override
    public void deleteChildInternal(ASTNode node) {
        if (ArrayUtil.contains(node.getPsi(), getParameters())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, node.getPsi());
        }
        super.deleteChildInternal(node);
    }

    public List<? extends PsiElement> getComponents() {
        return Arrays.asList(getParameters());
    }
}
