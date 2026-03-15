package consulo.ironPython.module.extension;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.SdkType;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionWithSdkBase;


/**
 * @author VISTALL
 * @since 11.02.14
 */
public class IronPythonModuleExtension extends ModuleExtensionWithSdkBase<IronPythonModuleExtension> implements BaseIronPythonModuleExtension<IronPythonModuleExtension>
{
	public IronPythonModuleExtension(String id, ModuleRootLayer modifiableRootModel)
	{
		super(id, modifiableRootModel);
	}

	@Override
	public Class<? extends SdkType> getSdkTypeClass()
	{
		return PythonSdkType.class;
	}
}
