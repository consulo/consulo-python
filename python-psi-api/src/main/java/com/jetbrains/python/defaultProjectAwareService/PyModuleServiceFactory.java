package com.jetbrains.python.defaultProjectAwareService;

import consulo.module.Module;

/**
 * This interface helps reduce the amount of boilerplate code when migrating services from the module-level to the project-level.
 * <p>
 * It can be used along with the default implementation {@link PyModuleServiceFactoryImpl} to delegate service instancing:
 *
 * <pre>{@code
 * @Service(Service.Level.PROJECT)
 * internal class PyDocumentationSettingsFactory
 *   : PyModuleServiceFactory<PyDocumentationSettings.ModuleService>
 *     by PyModuleServiceFactoryImpl(PyDocumentationSettings::ModuleService)
 * }</pre>
 * <p>
 * However, if there are specific needs for instancing, it is not required to use this interface.
 */
public interface PyModuleServiceFactory<T> {

  T getService(Module module);
}
