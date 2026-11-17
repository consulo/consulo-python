// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.namespacePackages;

import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactory;
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactoryImpl;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.inject.Singleton;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyNamespacePackagesServiceFactory implements PyModuleServiceFactory<PyNamespacePackagesService> {
    private final PyModuleServiceFactory<PyNamespacePackagesService> myDelegate =
        new PyModuleServiceFactoryImpl<>(PyNamespacePackagesService::new);

    public static PyNamespacePackagesServiceFactory getInstance(Project project) {
        return project.getInstance(PyNamespacePackagesServiceFactory.class);
    }

    @Override
    public PyNamespacePackagesService getService(Module module) {
        return myDelegate.getService(module);
    }
}
