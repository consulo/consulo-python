// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.defaultProjectAwareService;

import com.jetbrains.python.PythonFileType;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class PyDefaultProjectAwareModuleConfiguratorImpl<
  STATE,
  SERVICE extends PyDefaultProjectAwareService<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE>,
  APP_SERVICE extends SERVICE,
  MODULE_SERVICE extends SERVICE,
  MODULE_SERVICE_FACTORY extends PyModuleServiceFactory<MODULE_SERVICE>> implements PyDefaultProjectAwareServiceModuleConfigurator {
  private final PyDefaultProjectAwareServiceClasses<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE, MODULE_SERVICE_FACTORY> myClasses;
  private final @Nullable Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> myAutoDetector;

  public PyDefaultProjectAwareModuleConfiguratorImpl(PyDefaultProjectAwareServiceClasses<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE, MODULE_SERVICE_FACTORY> classes) {
    this(classes, null);
  }

  public PyDefaultProjectAwareModuleConfiguratorImpl(PyDefaultProjectAwareServiceClasses<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE, MODULE_SERVICE_FACTORY> classes,
                                                     @Nullable Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> autoDetector) {
    myClasses = classes;
    myAutoDetector = autoDetector;
  }

  @Override
  public void configureModule(Module module, boolean newProject) {
    Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> detector = myAutoDetector;
    if (newProject || detector == null) {
      // For new project only copy from app.
      // For opened only copy if no autoconfiguration
      myClasses.copyFromAppToModule(module);
      return;
    }


    //Try to autodetect
    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> autodetectSettings(module, detector), 10, TimeUnit.SECONDS);
  }

  private void autodetectSettings(Module module,
                                  Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> detector) {
    final String extension = PythonFileType.INSTANCE.getDefaultExtension();
    // Module#getModuleScope() and GlobalSearchScope#getModuleScope() search only in source roots
    Project project = module.getProject();
    // Consulo: ModulesScope is not an API, GlobalSearchScope#moduleContentScope covers the same module content
    final GlobalSearchScope searchScope = GlobalSearchScope.moduleContentScope(module);

    Collection<VirtualFile> pyFiles = ReadAction.nonBlocking(() -> FilenameIndex
        .getAllFilesByExt(project, extension, searchScope))
      .inSmartMode(project)
      .executeSynchronously();

    STATE state = detector.apply(Pair.create(module, pyFiles));
    if (state != null) {
      myClasses.getModuleService(module).loadState(state);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> myClasses.copyFromAppToModule(module));
    }
  }
}
