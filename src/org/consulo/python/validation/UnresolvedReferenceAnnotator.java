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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiReference;
import org.consulo.python.psi.PsiReferenceEx;
import org.consulo.python.psi.PyElement;

public class UnresolvedReferenceAnnotator extends PyAnnotator {
	@Override
	public void visitPyElement(PyElement node) {
		super.visitPyElement(node);

		for (PsiReference reference : node.getReferences())
			if ((!reference.isSoft()) &&
					(reference.resolve() == null)) {
				String text = reference.getElement().getText();
				String description;
				if ((reference instanceof PsiReferenceEx)) {
					PsiReferenceEx expression = (PsiReferenceEx) reference;
					if (!expression.shouldHighlightIfUnresolved()) {
						continue;
					}
					description = expression.getUnresolvedDescription();
				} else {
					description = "Unresolved reference '" + reference.getRangeInElement().substring(text) + "'";
				}

				Annotation annotation = getHolder().createErrorAnnotation(reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset()), description);

				annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
			}
	}
}