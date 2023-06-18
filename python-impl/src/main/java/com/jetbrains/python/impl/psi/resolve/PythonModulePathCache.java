/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.module.Module;
import consulo.module.content.layer.event.ModuleRootAdapter;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.virtualFileSystem.VirtualFileManager;

/**
 * @author yole
 */
public class PythonModulePathCache extends PythonPathCache implements Disposable
{
  public static PythonPathCache getInstance(Module module) {
    return module.getInstance(PythonPathCache.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public PythonModulePathCache(final Module module) {
    module.getMessageBus().connect().subscribe(ModuleRootListener.class, new ModuleRootAdapter() {
      public void rootsChanged(ModuleRootEvent event) {
        updateCacheForSdk(module);
        clearCache();
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileAdapter(), this);
    updateCacheForSdk(module);
  }

  private static void updateCacheForSdk(Module module) {
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      // initialize cache for SDK
      PythonSdkPathCache.getInstance(module.getProject(), sdk);
    }
  }

  @Override
  public void dispose() {
 }
}
