package com.jetbrains.python;

import org.jetbrains.annotations.NotNull;
import com.intellij.ide.IconDescriptor;
import com.intellij.ide.IconDescriptorUpdater;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;

/**
 * @author yole
 */
public class PyDirectoryIconProvider implements IconDescriptorUpdater
{
	@Override
	public void updateIcon(@NotNull IconDescriptor iconDescriptor, @NotNull PsiElement element, int i)
	{
		if(element instanceof PsiDirectory)
		{
			final PsiDirectory directory = (PsiDirectory) element;
			if(directory.findFile(PyNames.INIT_DOT_PY) != null)
			{
				final VirtualFile vFile = directory.getVirtualFile();
				final VirtualFile root = ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getSourceRootForFile(vFile);
				if(!Comparing.equal(root, vFile))
				{
					iconDescriptor.setMainIcon(PlatformIcons.PACKAGE_ICON);
				}
			}
		}
	}
}
