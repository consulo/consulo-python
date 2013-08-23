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
import ru.yole.pythonid.psi.PyElementVisitor;

public abstract class PyAnnotator extends PyElementVisitor {
	private AnnotationHolder _holder;

	public AnnotationHolder getHolder() {
		return this._holder;
	}

	public void setHolder(AnnotationHolder holder) {
		this._holder = holder;
	}
}