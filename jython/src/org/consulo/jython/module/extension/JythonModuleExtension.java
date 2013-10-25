package org.consulo.jython.module.extension;

import org.consulo.module.extension.impl.ModuleExtensionImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonModuleExtension extends ModuleExtensionImpl<JythonModuleExtension>
{
	public JythonModuleExtension(@NotNull String id, @NotNull Module module)
	{
		super(id, module);
	}
}
