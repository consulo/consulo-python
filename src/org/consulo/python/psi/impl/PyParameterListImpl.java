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

package org.consulo.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import org.consulo.python.PyElementTypes;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyParameter;
import org.consulo.python.psi.PyParameterList;


public class PyParameterListImpl extends PyElementImpl
		implements PyParameterList {
	private final TokenSet PARAMETER_FILTER = TokenSet.create(PyElementTypes.FORMAL_PARAMETER);

	public PyParameterListImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyParameterList(this);
	}

	@Override
	public PyParameter[] getParameters() {
		ASTNode[] nodes = getNode().getChildren(this.PARAMETER_FILTER);
		PyParameter[] params = new PyParameter[nodes.length];
		for (int i = 0; i < params.length; i++) {
			params[i] = ((PyParameter) nodes[i].getPsi());
		}
		return params;
	}
}