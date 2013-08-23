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

import org.consulo.python.psi.PyArgumentList;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyKeywordArgument;

import java.util.HashSet;
import java.util.Set;

public class ArgumentListAnnotator extends PyAnnotator {
	@Override
	public void visitPyArgumentList(PyArgumentList node) {
		PyExpression[] arguments = node.getArguments();
		boolean hadKeywordArguments = false;
		Set<String> keywords = new HashSet<String>();
		for (PyExpression argument : arguments)
			if ((argument instanceof PyKeywordArgument)) {
				hadKeywordArguments = true;
				PyKeywordArgument keyArgument = (PyKeywordArgument) argument;
				String keyword = keyArgument.getKeyword();
				if (!keywords.add(keyword)) {
					getHolder().createErrorAnnotation(keyArgument.getKeywordNode(), "duplicate keyword argument");
				}

			} else if (hadKeywordArguments) {
				getHolder().createErrorAnnotation(argument, "non-keyword arg after keyword arg");
			}
	}
}