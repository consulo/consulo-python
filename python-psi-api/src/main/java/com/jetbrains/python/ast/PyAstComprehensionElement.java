// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.PyComprehensionComponent;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyComprehensionIfComponent;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiNamedElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface PyAstComprehensionElement extends PyAstExpression, PyAstNamedElementContainer {
    /**
     * In "[x+1 for x in (1,2,3)]" result expression is "x+1".
     *
     * @return result expression.
     */
    @RequiredReadAction
    default @Nullable PyAstExpression getResultExpression() {
        ASTNode[] exprs = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
        return exprs.length == 0 ? null : (PyAstExpression)exprs[0].getPsi();
    }

    @RequiredReadAction
    default List<PyComprehensionComponent> getComponents() {
        List<PyComprehensionComponent> list = new ArrayList<>(5);
        visitComponents(new ComprehensionElementVisitor() {
            @Override
            void visitForComponent(PyComprehensionForComponent component) {
                list.add(component);
            }

            @Override
            void visitIfComponent(PyComprehensionIfComponent component) {
                list.add(component);
            }
        });
        return list;
    }

    /**
     * In "[x+1 for x in (1,2,3)]" a "for component" is "x".
     * @return all "for components"
     */
    @RequiredReadAction
    default List<PyComprehensionForComponent> getForComponents() {
        List<PyComprehensionForComponent> list = new ArrayList<>(5);
        visitComponents(new ComprehensionElementVisitor() {
            @Override
            void visitForComponent(PyComprehensionForComponent component) {
                list.add(component);
            }
        });
        return list;
    }

    @RequiredReadAction
    default List<PyComprehensionIfComponent> getIfComponents() {
        List<PyComprehensionIfComponent> list = new ArrayList<>(5);
        visitComponents(new ComprehensionElementVisitor() {
            @Override
            void visitIfComponent(PyComprehensionIfComponent component) {
                list.add(component);
            }
        });
        return list;
    }

    @Override
    @RequiredReadAction
    default List<PsiNamedElement> getNamedElements() {
        // extract whatever names are defined in "for" components
        List<? extends PyComprehensionForComponent> fors = getForComponents();
        PyAstExpression[] for_targets = new PyAstExpression[fors.size()];
        int i = 0;
        for (PyComprehensionForComponent for_comp : fors) {
            for_targets[i] = for_comp.getIteratorVariable();
            i += 1;
        }
        List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndLists(for_targets);
        List<PsiNamedElement> results = new ArrayList<>();
        for (PyAstExpression expression : expressions) {
            if (expression instanceof PsiNamedElement) {
                results.add((PsiNamedElement)expression);
            }
        }
        return results;
    }

    private static @Nullable ASTNode getNextExpression(ASTNode after) {
        ASTNode node = after;
        do {
            node = node.getTreeNext();
        }
        while (node != null && !PythonDialectsTokenSetProvider.getInstance().getExpressionTokens().contains(node.getElementType()));
        return node;
    }

    private void visitComponents(ComprehensionElementVisitor visitor) {
        ASTNode node = getNode().getFirstChildNode();
        while (node != null) {
            IElementType type = node.getElementType();
            ASTNode next = getNextExpression(node);
            if (next == null) {
                break;
            }
            if (type == PyTokenTypes.FOR_KEYWORD) {
                ASTNode next2 = getNextExpression(next);
                if (next2 == null) {
                    break;
                }
                PyAstExpression variable = (PyAstExpression)next.getPsi();
                PyAstExpression iterated = (PyAstExpression)next2.getPsi();
                boolean isAsync = Optional
                    .ofNullable(node.getTreePrev())
                    .map(ASTNode::getTreePrev)
                    .map(asyncNode -> asyncNode.getElementType() == PyTokenTypes.ASYNC_KEYWORD)
                    .orElse(false);

                visitor.visitForComponent(new PyComprehensionForComponent() {
                    @Override
                    public PyAstExpression getIteratorVariable() {
                        return variable;
                    }

                    @Override
                    public PyAstExpression getIteratedList() {
                        return iterated;
                    }

                    @Override
                    public boolean isAsync() {
                        return isAsync;
                    }
                });
            }
            else if (type == PyTokenTypes.IF_KEYWORD) {
                PyAstExpression test = (PyAstExpression)next.getPsi();
                visitor.visitIfComponent(new PyComprehensionIfComponent() {
                    @Override
                    public PyAstExpression getTest() {
                        return test;
                    }
                });
            }
            node = node.getTreeNext();
        }
    }

    abstract class ComprehensionElementVisitor {
        void visitIfComponent(PyComprehensionIfComponent component) {
        }

        void visitForComponent(PyComprehensionForComponent component) {
        }
    }
}
