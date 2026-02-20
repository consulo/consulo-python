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
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.highlighting.PyHighlighter;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * Marks built-in names.
 *
 * @author dcheryasov
 */
public class PyBuiltinAnnotator extends PyAnnotator
{
	@Override
	public void visitPyReferenceExpression(PyReferenceExpression node)
	{
		String name = node.getName();
		if(name == null)
		{
			return;
		}
		boolean highlightedAsAttribute = highlightAsAttribute(node, name);
		if(!highlightedAsAttribute && PyBuiltinCache.isInBuiltins(node))
		{
			PsiElement parent = node.getParent();

			AnnotationBuilder builder = getHolder().newSilentAnnotation(HighlightSeverity.INFORMATION);
			if(parent instanceof PyDecorator)
			{
				// don't mark the entire decorator, only mark the "@", else we'll conflict with deco annotator
				builder = builder.range(parent.getFirstChild()); // first child is there, or we'd not parse as deco
			}
			else
			{
				builder = builder.range(node);
			}
			
			builder = builder.textAttributes(PyHighlighter.PY_BUILTIN_NAME);
			builder.create();
		}
	}

	@Override
	public void visitPyTargetExpression(PyTargetExpression node)
	{
		String name = node.getName();
		if(name != null)
		{
			highlightAsAttribute(node, name);
		}
	}

	/**
	 * Try to highlight a node as a class attribute.
	 *
	 * @param node what to work with
	 * @return true iff the node was highlighted.
	 */
	private boolean highlightAsAttribute(@Nonnull PyQualifiedExpression node, @Nonnull String name)
	{
		LanguageLevel languageLevel = LanguageLevel.forElement(node);
		if(PyNames.UnderscoredAttributes.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name))
		{
			// things like __len__: foo.__len__ or class Foo: ... __len__ = my_len_impl
			if(node.isQualified() || ScopeUtil.getScopeOwner(node) instanceof PyClass)
			{
				ASTNode astNode = node.getNode();
				if(astNode != null)
				{
					ASTNode tgt = astNode.findChildByType(PyTokenTypes.IDENTIFIER); // only the id, not all qualifiers subtree
					if(tgt != null)
					{
						getHolder().newSilentAnnotation(HighlightSeverity.INFORMATION).range(tgt).textAttributes(PyHighlighter.PY_PREDEFINED_USAGE).create();
						return true;
					}
				}
			}
		}
		return false;
	}
}
