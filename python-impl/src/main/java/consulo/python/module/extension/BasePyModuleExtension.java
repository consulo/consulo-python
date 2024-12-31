/*
 * Copyright 2013-2014 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package consulo.python.module.extension;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.SdkType;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionWithSdkBase;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 31.01.14
 */
public class BasePyModuleExtension extends ModuleExtensionWithSdkBase<BasePyModuleExtension> implements PyModuleExtension<BasePyModuleExtension> {
  public BasePyModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module) {
    super(id, module);
  }

  @Nonnull
  @Override
  public Class<? extends SdkType> getSdkTypeClass() {
    return PythonSdkType.class;
  }
}
