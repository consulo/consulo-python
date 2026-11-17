package com.jetbrains.python.defaultProjectAwareService;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.module.Module;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Top-level class of JB {@code PyModuleServiceFactory.kt}.
 */
public class PyModuleServiceFactoryImpl<T> implements PyModuleServiceFactory<T> {
  private final Function<Module, T> serviceFactory;
  private final ConcurrentMap<Module, T> instances = new ConcurrentHashMap<>();

  public PyModuleServiceFactoryImpl(Function<Module, T> serviceFactory) {
    this.serviceFactory = serviceFactory;
  }

  @Override
  public T getService(Module module) {
    return instances.computeIfAbsent(module, m -> {
      T service = serviceFactory.apply(module);
      Disposer.register(module, () -> instances.remove(module));
      if (service instanceof Disposable disposable) {
        Disposer.register(module, disposable);
      }
      return service;
    });
  }
}
