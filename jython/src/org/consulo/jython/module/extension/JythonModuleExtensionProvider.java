package org.consulo.jython.module.extension;

import javax.swing.Icon;

import org.consulo.jython.JythonIcons;
import org.consulo.module.extension.ModuleExtensionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonModuleExtensionProvider implements ModuleExtensionProvider<JythonModuleExtension, JythonMutableModuleExtension>
{
	@Nullable
	@Override
	public Icon getIcon()
	{
		return JythonIcons.Jython;
	}

	@NotNull
	@Override
	public String getName()
	{
		return "Jython";
	}

	@NotNull
	@Override
	public Class<JythonModuleExtension> getImmutableClass()
	{
		return JythonModuleExtension.class;
	}

	@NotNull
	@Override
	public JythonModuleExtension createImmutable(@NotNull String s, @NotNull Module module)
	{
		return new JythonModuleExtension(s, module);
	}

	@NotNull
	@Override
	public JythonMutableModuleExtension createMutable(@NotNull String s, @NotNull Module module, @NotNull JythonModuleExtension jythonModuleExtension)
	{
		return new JythonMutableModuleExtension(s, module, jythonModuleExtension);
	}
}
