package org.consulo.python.module.extension;

import javax.swing.Icon;

import org.consulo.module.extension.ModuleExtensionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import icons.PythonIcons;

/**
 * @author VISTALL
 * @since 27.09.13.
 */
public class BasePyModuleExtensionProvider implements ModuleExtensionProvider<BasePyModuleExtension, BasePyMutableModuleExtension>
{
	@Nullable
	@Override
	public Icon getIcon()
	{
		return PythonIcons.Python.Python;
	}

	@NotNull
	@Override
	public String getName()
	{
		return "Python";
	}

	@NotNull
	@Override
	public BasePyModuleExtension createImmutable(@NotNull String s, @NotNull Module module)
	{
		return new BasePyModuleExtension(s, module);
	}

	@NotNull
	@Override
	public BasePyMutableModuleExtension createMutable(@NotNull String s, @NotNull Module module)
	{
		return new BasePyMutableModuleExtension(s, module);
	}
}
