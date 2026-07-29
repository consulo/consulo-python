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
package com.jetbrains.python.impl.refactoring.unwrap;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.unwrap.AbstractUnwrapper;
import consulo.language.ast.ASTNode;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.impl.PyIfPartIfImpl;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author ktisha
 */
public abstract class PyUnwrapper extends AbstractUnwrapper<PyUnwrapper.Context> {
    public PyUnwrapper(LocalizeValue description) {
        super(description.get());
    }

    @Override
    protected Context createContext() {
        return new Context();
    }

    @Override
    public List<PsiElement> unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
        List<PsiElement> res = super.unwrap(editor, element);
        for (PsiElement e : res) {
            CodeEditUtil.markToReformat(e.getNode(), true);
        }
        return res;
    }


    protected static class Context extends AbstractUnwrapper.AbstractContext {
        @RequiredReadAction
        public void extractPart(@Nullable PsiElement from) {
            if (from instanceof PyStatementWithElse stmtWithElse) {
                extractFromConditionalBlock(stmtWithElse);
            }
            else if (from instanceof PyStatementPart stmtPart) {
                extractFromElseBlock(stmtPart);
            }
            else if (from instanceof PyWithStatement withStmt) {
                extractFromWithBlock(withStmt);
            }
        }

        @RequiredReadAction
        public void extractFromConditionalBlock(PyStatementWithElse from) {
            PyStatementList statementList = null;
            if (from instanceof PyIfStatement ifStmt) {
                if (ifStmt.getIfPart() instanceof PyIfPartIfImpl ifPart) {
                    statementList = ifPart.getStatementList();
                }
            }
            else if (from instanceof PyWhileStatement whileStmt) {
                statementList = whileStmt.getWhilePart().getStatementList();
            }
            else if (from instanceof PyTryExceptStatement tryExceptStmt) {
                statementList = tryExceptStmt.getTryPart().getStatementList();
            }
            else if (from instanceof PyForStatement forStmt) {
                statementList = forStmt.getForPart().getStatementList();
            }
            if (statementList != null) {
                extract(statementList.getFirstChild(), statementList.getLastChild(), from);
            }
        }

        @RequiredReadAction
        public void extractFromElseBlock(PyStatementPart from) {
            PyStatementList body = from.getStatementList();
            if (body != null) {
                extract(body.getFirstChild(), body.getLastChild(), from.getParent());
            }
        }

        @RequiredReadAction
        public void extractFromWithBlock(PyWithStatement from) {
            ASTNode n = from.getNode().findChildByType(PyElementTypes.STATEMENT_LISTS);
            if (n != null) {
                PyStatementList body = (PyStatementList) n.getPsi();
                if (body != null) {
                    extract(body.getFirstChild(), body.getLastChild(), from);
                }
            }
        }

        @Override
        protected boolean isWhiteSpace(PsiElement element) {
            return element instanceof PsiWhiteSpace;
        }
    }
}
