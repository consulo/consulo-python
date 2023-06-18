/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.testing;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.impl.packaging.PyPackageUtil;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.component.messagebus.MessageBus;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.logging.Logger;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileContentChangeEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: catherine
 */
public class VFSTestFrameworkListener {
  private static final Logger LOG = Logger.getInstance(VFSTestFrameworkListener.class);

  private final AtomicBoolean myIsUpdating = new AtomicBoolean(false);
  private final PyTestFrameworkService myService = PyTestFrameworkService.getInstance();
  private final MergingUpdateQueue myQueue;

  public static VFSTestFrameworkListener getInstance() {
    return ApplicationManager.getApplication().getComponent(VFSTestFrameworkListener.class);
  }

  public VFSTestFrameworkListener() {
    final Application application = ApplicationManager.getApplication();
    final MessageBus messageBus = application.getMessageBus();
    messageBus.connect().subscribe(BulkFileListener.class, new BulkFileListener.Adapter() {
      @Override
      public void after(@Nonnull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (!(event.getFileSystem() instanceof LocalFileSystem) || event instanceof VFileContentChangeEvent) {
            continue;
          }
          final String path = event.getPath();
          final boolean containsNose = path.contains(PyNames.NOSE_TEST);
          final boolean containsPy = path.contains("py-1") || path.contains(PyNames.PY_TEST);
          final boolean containsAt = path.contains(PyNames.AT_TEST);
          if (!containsAt && !containsNose && !containsPy) {
            continue;
          }
          for (Sdk sdk : PythonSdkType.getAllSdks()) {
            if (PySdkUtil.isRemote(sdk)) {
              continue;
            }
            for (VirtualFile virtualFile : sdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance())) {
              final String root = virtualFile.getCanonicalPath();
              if (root != null && path.contains(root)) {
                if (containsNose) {
                  scheduleTestFrameworkCheck(sdk, PyNames.NOSE_TEST);
                  return;
                }
                else if (containsPy) {
                  scheduleTestFrameworkCheck(sdk, PyNames.PY_TEST);
                  return;
                }
                else {
                  scheduleTestFrameworkCheck(sdk, PyNames.AT_TEST);
                  return;
                }
              }
            }
          }
        }
      }
    });
    myQueue = new MergingUpdateQueue("TestFrameworkChecker", 5000, true, null, application, null, Alarm.ThreadToUse.POOLED_THREAD);
  }

  public void updateAllTestFrameworks(@Nonnull Sdk sdk) {
    final Map<String, Boolean> whichInstalled = checkTestFrameworksInstalled(sdk, PyNames.PY_TEST, PyNames.NOSE_TEST, PyNames.AT_TEST);
    ApplicationManager.getApplication().invokeLater(() -> {
      for (Map.Entry<String, Boolean> entry : whichInstalled.entrySet()) {
        final Boolean installed = entry.getValue();
        if (installed != null) {
          //noinspection ConstantConditions
          setTestFrameworkInstalled(installed, sdk.getHomePath(), entry.getKey());
        }
      }
    });
  }

  private void scheduleTestFrameworkCheck(@Nonnull Sdk sdk, @Nonnull String testPackageName) {
    myQueue.queue(new Update(Pair.create(sdk, testPackageName)) {
      @Override
      public void run() {
        checkFrameworkInstalledAndUpdateSettings(sdk, testPackageName);
      }
    });
  }

  private void checkFrameworkInstalledAndUpdateSettings(@Nullable Sdk sdk, @Nonnull String testPackageName) {
    final Boolean installed = checkTestFrameworkInstalled(sdk, testPackageName);
    if (installed != null) {
      //noinspection ConstantConditions
      ApplicationManager.getApplication().invokeLater(() -> setTestFrameworkInstalled(installed, sdk.getHomePath(), testPackageName));
    }
  }

  /**
   * @return null if we can't be sure
   */
  @Contract("null, _ -> null")
  private Boolean checkTestFrameworkInstalled(@Nullable Sdk sdk, @Nonnull String testPackageName) {
    return checkTestFrameworksInstalled(sdk, testPackageName).get(testPackageName);
  }

  @Nonnull
  private Map<String, Boolean> checkTestFrameworksInstalled(@Nullable Sdk sdk, @Nonnull String... testPackageNames) {
    final Map<String, Boolean> result = new HashMap<>();
    if (sdk == null || StringUtil.isEmptyOrSpaces(sdk.getHomePath())) {
      LOG.info("Searching test runner in empty sdk");
      return result;
    }
    final PyPackageManager manager = PyPackageManager.getInstance(sdk);
    final boolean refreshed = PyPackageUtil.updatePackagesSynchronouslyWithGuard(manager, myIsUpdating);
    if (refreshed) {
      final List<PyPackage> packages = manager.getPackages();
      if (packages != null) {
        for (String name : testPackageNames) {
          result.put(name, PyPackageUtil.findPackage(packages, name) != null);
        }
      }
    }
    return result;
  }

  private void setPyTestInstalled(boolean installed, @Nonnull String sdkHome) {
    myService.SDK_TO_PYTEST.put(sdkHome, installed);
  }

  public boolean isPyTestInstalled(@Nonnull Sdk sdk) {
    final Boolean isInstalled = myService.SDK_TO_PYTEST.get(sdk.getHomePath());
    if (isInstalled == null) {
      scheduleTestFrameworkCheck(sdk, PyNames.PY_TEST);
      return true;
    }
    return isInstalled;
  }

  private void setNoseTestInstalled(boolean installed, @Nonnull String sdkHome) {
    myService.SDK_TO_NOSETEST.put(sdkHome, installed);
  }

  public boolean isNoseTestInstalled(@Nonnull Sdk sdk) {
    final Boolean isInstalled = myService.SDK_TO_NOSETEST.get(sdk.getHomePath());
    if (isInstalled == null) {
      scheduleTestFrameworkCheck(sdk, PyNames.NOSE_TEST);
      return true;
    }
    return isInstalled;
  }

  private void setAtTestInstalled(boolean installed, @Nonnull String sdkHome) {
    myService.SDK_TO_ATTEST.put(sdkHome, installed);
  }

  public boolean isAtTestInstalled(@Nonnull Sdk sdk) {
    final Boolean isInstalled = myService.SDK_TO_ATTEST.get(sdk.getHomePath());
    if (isInstalled == null) {
      scheduleTestFrameworkCheck(sdk, PyNames.AT_TEST);
      return true;
    }
    return isInstalled;
  }

  public void setTestFrameworkInstalled(boolean installed, @Nonnull String sdkHome, @Nonnull String name) {
    switch (name) {
      case PyNames.NOSE_TEST:
        setNoseTestInstalled(installed, sdkHome);
        break;
      case PyNames.PY_TEST:
        setPyTestInstalled(installed, sdkHome);
        break;
      case PyNames.AT_TEST:
        setAtTestInstalled(installed, sdkHome);
        break;
    }
  }
}
