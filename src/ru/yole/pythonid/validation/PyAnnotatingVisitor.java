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

package ru.yole.pythonid.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;

import java.util.Set;

public class PyAnnotatingVisitor
		implements Annotator {
	private static final Logger LOGGER = Logger.getInstance(PyAnnotatingVisitor.class.getName());
	private Set<? extends Class<? extends PyAnnotator>> _annotators;

	public PyAnnotatingVisitor(Set<? extends Class<? extends PyAnnotator>> annotators) {
		this._annotators = annotators;
	}

	@Override
	public void annotate(PsiElement psiElement, AnnotationHolder holder) {
		for (Class cls : this._annotators) {
			PyAnnotator annotator;
			try {
				annotator = (PyAnnotator) cls.newInstance();
			} catch (InstantiationException e) {
				LOGGER.error(e);
				continue;
			} catch (IllegalAccessException e) {
				LOGGER.error(e);
			}
			continue;

			annotator.setHolder(holder);
			try {
				psiElement.accept(annotator);
			} catch (StopCurrentAnnotatorException e) {
			}
		}
	}
}