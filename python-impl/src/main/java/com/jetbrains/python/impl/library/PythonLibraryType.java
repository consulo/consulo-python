// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.library;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.library.DummyLibraryProperties;
import consulo.content.library.LibraryType;
import consulo.content.library.NewLibraryConfiguration;
import consulo.content.library.PersistentLibraryKind;
import consulo.content.library.ui.LibraryEditorComponent;
import consulo.content.library.ui.LibraryPropertiesEditor;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * python library type should be used if library paths to be included in Python Path
 */
@ExtensionImpl
public final class PythonLibraryType extends LibraryType<DummyLibraryProperties> {
    private static final PersistentLibraryKind<DummyLibraryProperties> LIBRARY_KIND = new PersistentLibraryKind<>("python") {
        @Override
        public DummyLibraryProperties createDefaultProperties() {
            return DummyLibraryProperties.INSTANCE;
        }
    };

    // Consulo: the extension is created by the container, so the constructor is public
    public PythonLibraryType() {
        super(LIBRARY_KIND);
    }

    public static PythonLibraryType getInstance() {
        return Application.get().getExtensionPoint(LibraryType.class).findExtensionOrFail(PythonLibraryType.class);
    }

    @Override
    public @Nullable String getCreateActionName() {
        return null;
    }

    // Consulo: JB returns null, Consulo returns a future - a cancelled one means no library
    @RequiredUIAccess
    @Override
    public CompletableFuture<NewLibraryConfiguration> createNewLibrary(@Nullable Component parentComponent,
                                                                       @Nullable VirtualFile contextDirectory,
                                                                       Project project) {
        return CompletableFuture.failedFuture(new CancellationException());
    }

    @Override
    public @Nullable LibraryPropertiesEditor createPropertiesEditor(LibraryEditorComponent<DummyLibraryProperties> editorComponent) {
        return null;
    }

    @Override
    public @Nullable Image getIcon() {
        return null;
    }

}
