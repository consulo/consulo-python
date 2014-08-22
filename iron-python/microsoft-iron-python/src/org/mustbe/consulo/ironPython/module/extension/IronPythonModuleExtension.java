package org.mustbe.consulo.ironPython.module.extension;

import org.consulo.module.extension.impl.ModuleExtensionWithSdkImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleRootLayer;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author VISTALL
 * @since 11.02.14
 */
public class IronPythonModuleExtension extends ModuleExtensionWithSdkImpl<IronPythonModuleExtension> implements
		BaseIronPythonModuleExtension<IronPythonModuleExtension>
{
	public IronPythonModuleExtension(@NotNull String id, @NotNull ModuleRootLayer modifiableRootModel)
	{
		super(id, modifiableRootModel);
	}

	@NotNull
	@Override
	public Class<? extends SdkType> getSdkTypeClass()
	{
		return PythonSdkType.class;
	}
}
