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

package org.consulo.python.psi;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyResolveUtil {
	@Nullable
	public static PsiElement treeWalkUp(PsiScopeProcessor processor, PsiElement elt, PsiElement lastParent, PsiElement place) {
		if (elt == null) return null;

		PsiElement cur = elt;
		do {
			if ((!cur.processDeclarations(processor, ResolveState.initial(), cur == elt ? lastParent : null, place)) &&
					((processor instanceof ResolveProcessor))) {
				return ((ResolveProcessor) processor).getResult();
			}

			if ((cur instanceof PsiFile)) break;
			cur = cur.getPrevSibling();
		} while (cur != null);

		return treeWalkUp(processor, elt.getContext(), elt, place);
	}

	public static class VariantsProcessor
			implements PsiScopeProcessor {
		private List<PsiElement> _names = new ArrayList<PsiElement>();

		public PsiElement[] getResult() {
			return this._names.toArray(new PsiElement[this._names.size()]);
		}

		@Override
		public boolean execute(@NotNull PsiElement element, ResolveState resolveState) {
			if ((element instanceof PsiNamedElement)) {
				this._names.add(element);
			} else if ((element instanceof PyReferenceExpression)) {
				PyReferenceExpression expr = (PyReferenceExpression) element;
				String referencedName = expr.getReferencedName();
				if (referencedName != null) {
					this._names.add(element);
				}
			}

			return true;
		}

		@Nullable
		@Override
		public <T> T getHint(@NotNull Key<T> tKey) {
			return null;
		}

		@Override
		public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
		}
	}

	public static class MultiResolveProcessor
			implements PsiScopeProcessor {
		private String _name;
		private List<ResolveResult> _results = new ArrayList();

		public MultiResolveProcessor(String name) {
			this._name = name;
		}

		public ResolveResult[] getResults() {
			return this._results.toArray(new ResolveResult[this._results.size()]);
		}

		@Override
		public boolean execute(@NotNull PsiElement element, ResolveState resolveState) {
			if ((element instanceof PsiNamedElement)) {
				if (this._name.equals(((PsiNamedElement) element).getName())) {
					this._results.add(new PsiElementResolveResult(element));
				}
			} else if ((element instanceof PyReferenceExpression)) {
				PyReferenceExpression expr = (PyReferenceExpression) element;
				String referencedName = expr.getReferencedName();
				if ((referencedName != null) && (referencedName.equals(this._name))) {
					this._results.add(new PsiElementResolveResult(element));
				}
			}

			return true;
		}

		@Nullable
		@Override
		public <T> T getHint(@NotNull Key<T> tKey) {
			return null;
		}

		@Override
		public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
		}
	}

	public static class ResolveProcessor
			implements PsiScopeProcessor {
		private String _name;
		private PsiElement _result = null;

		public ResolveProcessor(String name) {
			this._name = name;
		}

		public PsiElement getResult() {
			return this._result;
		}

		@Override
		public boolean execute(@NotNull PsiElement element, ResolveState resolveState) {
			if ((element instanceof PsiNamedElement)) {
				if (this._name.equals(((PsiNamedElement) element).getName())) {
					this._result = element;
					return false;
				}
			} else if ((element instanceof PyReferenceExpression)) {
				PyReferenceExpression expr = (PyReferenceExpression) element;
				String referencedName = expr.getReferencedName();
				if ((referencedName != null) && (referencedName.equals(this._name))) {
					this._result = element;
					return false;
				}
			}

			return true;
		}

		@Nullable
		@Override
		public <T> T getHint(@NotNull Key<T> tKey) {
			return null;
		}

		@Override
		public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
		}
	}
}