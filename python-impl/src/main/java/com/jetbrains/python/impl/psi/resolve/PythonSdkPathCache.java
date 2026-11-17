// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import consulo.application.Application;
import consulo.component.messagebus.MessageBusConnection;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.event.SdkTableListener;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFileManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The resolve cache of the roots of one interpreter in one project, and its {@link PyBuiltinCache}. The project keeps one
 * per interpreter, see {@link #getInstance(Project, Sdk)}.
 */
public class PythonSdkPathCache extends PythonPathCache implements Disposable {
  private static final Key<Map<Sdk, PythonSdkPathCache>> KEY = Key.create("PythonPathCache");

  public static PythonSdkPathCache getInstance(Project project, Sdk interpreter) {
    synchronized (KEY) {
      Map<Sdk, PythonSdkPathCache> cacheMap = project.getUserData(KEY);
      if (cacheMap == null) {
        cacheMap = new HashMap<>();
        project.putUserData(KEY, cacheMap);
      }
      PythonSdkPathCache cache = cacheMap.get(interpreter);
      if (cache == null) {
        cache = new PythonSdkPathCache(project, interpreter);
        cacheMap.put(interpreter, cache);
      }
      return cache;
    }
  }

  private final Project myProject;
  private final Sdk myInterpreter;
  private final AtomicReference<PyBuiltinCache> myBuiltins = new AtomicReference<>();

  private PythonSdkPathCache(final Project project, final Sdk interpreter) {
    myProject = project;
    myInterpreter = interpreter;
    if (project.isDisposed()) {
      return;
    }
    Disposer.register(project, this);
    // The updater still writes the roots and the packages of an interpreter into its SDK.
    final Sdk sdk = interpreter;
    final MessageBusConnection connection = Application.get().getMessageBus().connect(this);
    connection.subscribe(SdkTableListener.class, new SdkTableListener() {
      @Override
      public void sdkRemoved(Sdk jdk) {
        if (jdk == sdk) {
          Disposer.dispose(PythonSdkPathCache.this);
        }
      }
    });
    // TODO consulo: PyPackageManager.PACKAGE_MANAGER_TOPIC missing
    sdk.getRootProvider().addRootSetChangedListener(wrapper -> onRootsChanged(), this);
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(), this);
  }

  /**
   * Drops what was resolved against the old roots of the interpreters of {@code project}, when
   * {@code PythonInterpreterDetails} reports a change of them.
   */
  static void onInterpreterDetailsChanged(Project project) {
    final List<PythonSdkPathCache> caches;
    synchronized (KEY) {
      final Map<Sdk, PythonSdkPathCache> cacheMap = project.getUserData(KEY);
      caches = cacheMap != null ? new ArrayList<>(cacheMap.values()) : List.of();
    }
    for (PythonSdkPathCache cache : caches) cache.onRootsChanged();
  }

  /**
   * Drops what was resolved against the old roots of the interpreter.
   */
  private void onRootsChanged() {
    clearCache();
    if (!myProject.isDisposed()) {
      final Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (Module module : modules) {
        PythonModulePathCache.getInstance(module).clearCache();
      }
    }
    myBuiltins.set(null);
  }

  @Override
  public void dispose() {
    synchronized (KEY) {
      final Map<Sdk, PythonSdkPathCache> cacheMap = myProject.getUserData(KEY);
      if (cacheMap != null) {
        cacheMap.remove(myInterpreter, this);
      }
    }
  }

  public PyBuiltinCache getBuiltins() {
    while (true) {
      PyBuiltinCache pyBuiltinCache = myBuiltins.get();
      if (pyBuiltinCache == null || !pyBuiltinCache.isValid()) {
        PyBuiltinCache newCache = new PyBuiltinCache(myProject, myInterpreter);
        if (myBuiltins.compareAndSet(pyBuiltinCache, newCache)) {
          return newCache;
        }
      }
      else {
        return pyBuiltinCache;
      }
    }
  }

  public void clearBuiltins() {
    myBuiltins.set(null);
  }
}
