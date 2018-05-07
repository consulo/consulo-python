package consulo.ironPython.module.extension;

import javax.annotation.Nonnull;

import com.intellij.openapi.projectRoots.SdkType;
import com.jetbrains.python.sdk.PythonSdkType;
import consulo.module.extension.impl.ModuleExtensionWithSdkImpl;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 11.02.14
 */
public class IronPythonModuleExtension extends ModuleExtensionWithSdkImpl<IronPythonModuleExtension> implements BaseIronPythonModuleExtension<IronPythonModuleExtension>
{
	public IronPythonModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer modifiableRootModel)
	{
		super(id, modifiableRootModel);
	}

	@Nonnull
	@Override
	public Class<? extends SdkType> getSdkTypeClass()
	{
		return PythonSdkType.class;
	}
}
