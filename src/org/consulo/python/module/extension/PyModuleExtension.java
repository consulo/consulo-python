package org.consulo.python.module.extension;

import org.consulo.module.extension.impl.ModuleExtensionWithSdkImpl;
import org.consulo.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.SdkType;

/**
 * @author VISTALL
 * @since 27.09.13.
 */
public class PyModuleExtension extends ModuleExtensionWithSdkImpl<PyModuleExtension>
{
	public PyModuleExtension(@NotNull String id, @NotNull Module module)
	{
		super(id, module);
	}

	@Override
	protected Class<? extends SdkType> getSdkTypeClass()
	{
		return PythonSdkType.class;
	}
}
