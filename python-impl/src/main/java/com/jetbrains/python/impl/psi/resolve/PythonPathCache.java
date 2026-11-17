// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.VirtualFileCopyEvent;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import org.jspecify.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class PythonPathCache {
  private final Map<QualifiedName, SoftReference<List<PsiElement>>> myCache = new ConcurrentHashMap<>();
  private final Map<String, List<QualifiedName>> myQNameCache = new ConcurrentHashMap<>();

  public void clearCache() {
    myCache.clear();
    myQNameCache.clear();
  }

  /***
   * @return null if nothing found in cache. If path resolves to nothing you get empty list
   */
  public @Nullable List<PsiElement> get(final QualifiedName qualifiedName) {
    final SoftReference<List<PsiElement>> references = myCache.get(qualifiedName);
    if (references == null) {
      return null;
    }
    final List<PsiElement> elements = references.get();
    if (elements != null && !elements.stream().allMatch(PsiElement::isValid)) {
      // At least one element is invalid
      return null;
    }
    return elements != null ? Collections.unmodifiableList(elements) : null;
  }

  public void put(QualifiedName qualifiedName, @Nullable List<PsiElement> results) {
    if (results != null) {
      myCache.put(qualifiedName, new SoftReference<>(results));
    }
  }

  public @Nullable List<QualifiedName> getNames(@Nullable VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    final List<QualifiedName> names = myQNameCache.get(vFile.getUrl());
    return names != null ? Collections.unmodifiableList(names) : null;
  }

  public void putNames(VirtualFile vFile, List<QualifiedName> qNames) {
    myQNameCache.put(vFile.getUrl(), new ArrayList<>(qNames));
  }

  protected class MyVirtualFileListener implements VirtualFileListener {
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
  }
}
