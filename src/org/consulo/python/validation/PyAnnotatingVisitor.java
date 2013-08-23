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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import org.consulo.lombok.annotations.Logger;

import java.util.LinkedHashSet;
import java.util.Set;

@Logger
public class PyAnnotatingVisitor implements Annotator {
	private static Set<Class<? extends PyAnnotator>> _annotators = new LinkedHashSet<Class<? extends PyAnnotator>>();

	static {
		_annotators.add(AssignTargetAnnotator.class);
		_annotators.add(ParameterListAnnotator.class);
		_annotators.add(ArgumentListAnnotator.class);
		_annotators.add(ReturnAnnotator.class);
		_annotators.add(TryExceptAnnotator.class);
		_annotators.add(BreakContinueAnnotator.class);
		_annotators.add(GlobalAnnotator.class);
		_annotators.add(DocStringAnnotator.class);
		_annotators.add(ImportAnnotator.class);
		_annotators.add(UnresolvedReferenceAnnotator.class);
	}

	@Override
	public void annotate(PsiElement psiElement, AnnotationHolder holder) {
		for (Class cls : _annotators) {
			PyAnnotator annotator;
			try {
				annotator = (PyAnnotator) cls.newInstance();
				annotator.setHolder(holder);

				try {
					psiElement.accept(annotator);
				}
				catch (StopCurrentAnnotatorException e) {
				}

			} catch (InstantiationException e) {
				LOGGER.error(e);
				continue;
			} catch (IllegalAccessException e) {
				LOGGER.error(e);
			}
		}
	}
}