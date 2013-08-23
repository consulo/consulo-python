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

package org.consulo.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import org.consulo.python.psi.PyClass;
import org.consulo.python.psi.PyElement;
import org.consulo.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

public class PyStructureViewModel extends TextEditorBasedStructureViewModel {
	private PyElement _root;

	public PyStructureViewModel(PyElement root) {
		super(root.getContainingFile());
		this._root = root;
	}

	@NotNull
	@Override
	public StructureViewTreeElement getRoot() {
		return new PyStructureViewElement(this._root);
	}

	@NotNull
	@Override
	public Grouper[] getGroupers() {
		return new Grouper[0];
	}

	@NotNull
	@Override
	public Sorter[] getSorters() {
		return new Sorter[]{Sorter.ALPHA_SORTER};
	}

	@NotNull
	@Override
	public Filter[] getFilters() {
		return new Filter[0];
	}

	@Override
	protected PsiFile getPsiFile() {
		return this._root.getContainingFile();
	}

	@NotNull
	@Override
	protected Class[] getSuitableClasses() {
		return new Class[]{PyFunction.class, PyClass.class};
	}
}