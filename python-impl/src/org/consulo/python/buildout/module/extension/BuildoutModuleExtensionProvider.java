package org.consulo.python.buildout.module.extension;

import javax.swing.Icon;

import org.consulo.module.extension.ModuleExtensionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import icons.PythonIcons;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutModuleExtensionProvider implements ModuleExtensionProvider<BuildoutModuleExtension,BuildoutMutableModuleExtension>
{
	@Nullable
	@Override
	public Icon getIcon()
	{
		return PythonIcons.Python.Buildout.Buildout;
	}

	@NotNull
	@Override
	public String getName()
	{
		return "Buildout";
	}

	@NotNull
	@Override
	public Class<BuildoutModuleExtension> getImmutableClass()
	{
		return BuildoutModuleExtension.class;
	}

	@NotNull
	@Override
	public BuildoutModuleExtension createImmutable(@NotNull String s, @NotNull Module module)
	{
		return new BuildoutModuleExtension(s, module);
	}

	@NotNull
	@Override
	public BuildoutMutableModuleExtension createMutable(@NotNull String s, @NotNull Module module, @NotNull BuildoutModuleExtension buildoutModuleExtension)
	{
		return new BuildoutMutableModuleExtension(s, module, buildoutModuleExtension);
	}
}
