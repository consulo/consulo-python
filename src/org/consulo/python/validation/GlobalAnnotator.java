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

package org.consulo.python.validation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.consulo.python.psi.*;

import java.util.HashSet;
import java.util.Set;

public class GlobalAnnotator extends PyAnnotator {
	@Override
	public void visitPyGlobalStatement(PyGlobalStatement node) {
		PyFunction function = node.getContainingElement(PyFunction.class);
		if (function != null) {
			PyParameterList paramList = function.getParameterList();
			PyParameter[] params = paramList.getParameters();
			Set paramNames = new HashSet();
			for (PyParameter param : params) {
				paramNames.add(param.getName());
			}
			for (PyReferenceExpression expr : node.getGlobals()) {
				if (paramNames.contains(expr.getReferencedName())) {
					getHolder().createErrorAnnotation(expr.getTextRange(), "name is used as both global and parameter");
				}
				PsiElement resolvedElement = expr.resolve();
				if ((resolvedElement != null) && (PsiTreeUtil.isAncestor(function, resolvedElement, true)))
					getHolder().createWarningAnnotation(expr.getTextRange(), "name '" + expr.getReferencedName() + "' is assigned to before global declaration");
			}
		}
	}
}