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

package com.jetbrains.python.impl.packaging;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.util.DateFormatUtil;
import consulo.content.bundle.Sdk;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * PyPI cache updater
 * User : catherine
 */
@ExtensionImpl
public class PyPIPackagesUpdater implements PostStartupActivity {
  private static final Logger LOG = Logger.getInstance(PyPIPackagesUpdater.class);

  @Override
  public void runActivity(@Nonnull final Project project, UIAccess uiAccess) {
    final Application application = ApplicationManager.getApplication();
    final PyPackageService service = PyPackageService.getInstance();
    if (checkNeeded(project, service)) {
      application.executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            PyPIPackageUtil.INSTANCE.updatePyPICache(service);
            service.LAST_TIME_CHECKED = System.currentTimeMillis();
          }
          catch (IOException e) {
            LOG.warn(e.getMessage());
          }
        }
      });
    }
  }


  public static boolean checkNeeded(Project project, PyPackageService service) {
    boolean hasPython = false;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
        hasPython = true;
        break;
      }
    }
    if (!hasPython) return false;
    final long timeDelta = System.currentTimeMillis() - service.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < DateFormatUtil.DAY) return false;
    return true;
  }
}
