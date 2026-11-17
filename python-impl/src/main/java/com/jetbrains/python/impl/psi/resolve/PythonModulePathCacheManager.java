// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.messagebus.MessageBusConnection;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.module.Module;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.VirtualFileCopyEvent;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PythonModulePathCacheManager implements Disposable {
  private final Project project;
  private final ConcurrentMap<Module, PythonModulePathCache> pathCache = new ConcurrentHashMap<>();

  @Inject
  public PythonModulePathCacheManager(Project project) {
    this.project = project;
    MessageBusConnection connection = project.getMessageBus().connect(this);

    connection.subscribe(ModuleRootListener.class, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        Set<Sdk> interpreters = new HashSet<>();
        for (Module module : pathCache.keySet()) {
          Sdk interpreter = MainPythonSdkKt.pythonSdkIfReady(module);
          if (interpreter != null) {
            interpreters.add(interpreter);
          }
        }
        for (Sdk interpreter : interpreters) {
          updateCacheForInterpreter(project, interpreter);
        }
        for (PythonModulePathCache cache : pathCache.values()) {
          cache.clearCache();
        }
      }
    });

    // TODO consulo: WorkspaceModelTopics.CHANGED missing
    // TODO consulo: PyPackageManager.PACKAGE_MANAGER_TOPIC missing

    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(pathCache), this);
  }

  private static final class MyVirtualFileListener implements VirtualFileListener {
    private final ConcurrentMap<Module, PythonModulePathCache> pathCache;

    private MyVirtualFileListener(ConcurrentMap<Module, PythonModulePathCache> pathCache) {
      this.pathCache = pathCache;
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      clearCache();
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      clearCache();
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      clearCache();
    }

    @Override
    public void fileCopied(VirtualFileCopyEvent event) {
      clearCache();
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        clearCache();
      }
    }

    private void clearCache() {
      for (PythonModulePathCache cache : pathCache.values()) {
        cache.clearCache();
      }
    }
  }

  public PythonModulePathCache getPythonPathCache(Module module) {
    return pathCache.computeIfAbsent(module, it -> {
      Disposer.register(module, () -> pathCache.remove(module));
      return new PythonModulePathCache(module);
    });
  }

  @Override
  public void dispose() {
  }

  public static PythonModulePathCacheManager getInstance(Project project) {
    return project.getInstance(PythonModulePathCacheManager.class);
  }

  public static void updateCacheForInterpreter(Project project, Sdk interpreter) {
    // initialize cache for the interpreter
    PythonSdkPathCache.getInstance(project, interpreter);
  }
}
