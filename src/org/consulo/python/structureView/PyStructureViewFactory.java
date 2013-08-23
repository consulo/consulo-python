package org.consulo.python.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import org.consulo.python.psi.PyElement;
import org.consulo.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author VISTALL
 * @since 23.08.13.
 */
public class PyStructureViewFactory implements PsiStructureViewFactory {

	@Nullable
	@Override
	public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
		if (psiFile instanceof PyFileImpl) {
			return new TreeBasedStructureViewBuilder() {
				@NotNull
				@Override
				public StructureViewModel createStructureViewModel() {
					return new PyStructureViewModel((PyElement) psiFile);
				}
			};
		}
		return null;
	}
}