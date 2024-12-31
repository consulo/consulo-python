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

package com.jetbrains.python.impl.sdk;

import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.application.ApplicationManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkAdditionalData;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.JDOMExternalizer;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.pointer.VirtualFilePointerContainer;
import consulo.virtualFileSystem.pointer.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PythonSdkAdditionalData implements SdkAdditionalData {
  @NonNls
  private static final String PATHS_ADDED_BY_USER_ROOT = "PATHS_ADDED_BY_USER_ROOT";
  @NonNls
  private static final String PATH_ADDED_BY_USER = "PATH_ADDED_BY_USER";
  @NonNls
  private static final String PATHS_REMOVED_BY_USER_ROOT = "PATHS_REMOVED_BY_USER_ROOT";
  @NonNls
  private static final String PATH_REMOVED_BY_USER = "PATH_REMOVED_BY_USER";
  @NonNls
  private static final String ASSOCIATED_PROJECT_PATH = "ASSOCIATED_PROJECT_PATH";

  private final VirtualFilePointerContainer myAddedPaths;
  private final VirtualFilePointerContainer myExcludedPaths;

  private final PythonSdkFlavor myFlavor;
  private String myAssociatedProjectPath;
  private boolean myAssociateWithNewProject;

  public PythonSdkAdditionalData(@Nullable PythonSdkFlavor flavor) {
    myFlavor = flavor;
    myAddedPaths = VirtualFilePointerManager.getInstance().createContainer(ApplicationManager.getApplication());
    myExcludedPaths = VirtualFilePointerManager.getInstance().createContainer(ApplicationManager.getApplication());
  }

  protected PythonSdkAdditionalData(@Nonnull PythonSdkAdditionalData from) {
    myFlavor = from.getFlavor();
    myAddedPaths = from.myAddedPaths.clone(ApplicationManager.getApplication());
    myExcludedPaths = from.myExcludedPaths.clone(ApplicationManager.getApplication());
    myAssociatedProjectPath = from.myAssociatedProjectPath;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return new PythonSdkAdditionalData(this);
  }

  public void setAddedPathsFromVirtualFiles(@Nonnull Set<VirtualFile> addedPaths) {
    myAddedPaths.killAll();
    for (VirtualFile file : addedPaths) {
      myAddedPaths.add(file);
    }
  }

  public void setExcludedPathsFromVirtualFiles(@Nonnull Set<VirtualFile> addedPaths) {
    myExcludedPaths.killAll();
    for (VirtualFile file : addedPaths) {
      myExcludedPaths.add(file);
    }
  }

  public String getAssociatedProjectPath() {
    return myAssociatedProjectPath;
  }

  public void setAssociatedProjectPath(@Nullable String associatedProjectPath) {
    myAssociatedProjectPath = associatedProjectPath;
  }

  public void associateWithProject(Project project) {
    final String path = project.getBasePath();
    if (path != null) {
      myAssociatedProjectPath = FileUtil.toSystemIndependentName(path);
    }
  }

  public void associateWithNewProject() {
    myAssociateWithNewProject = true;
  }

  public void reassociateWithCreatedProject(Project project) {
    if (myAssociateWithNewProject) {
      associateWithProject(project);
    }
  }

  public void save(@Nonnull final Element rootElement) {
    savePaths(rootElement, myAddedPaths, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER);
    savePaths(rootElement, myExcludedPaths, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER);

    if (myAssociatedProjectPath != null) {
      rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, myAssociatedProjectPath);
    }
  }

  private static void savePaths(Element rootElement, VirtualFilePointerContainer paths, String root, String element) {
    for (String addedPath : paths.getUrls()) {
      final Element child = new Element(root);
      child.setAttribute(element, addedPath);
      rootElement.addContent(child);
    }
  }

  @Nullable
  public PythonSdkFlavor getFlavor() {
    return myFlavor;
  }

  @Nonnull
  public static PythonSdkAdditionalData load(Sdk sdk, @Nullable Element element) {
    final PythonSdkAdditionalData data = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
    data.load(element);
    return data;
  }

  protected void load(@Nullable Element element) {
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER), myAddedPaths);
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER), myExcludedPaths);
    if (element != null) {
      setAssociatedProjectPath(element.getAttributeValue(ASSOCIATED_PROJECT_PATH));
    }
  }

  private static void collectPaths(@Nonnull List<String> paths, VirtualFilePointerContainer container) {
    for (String path : paths) {
      if (StringUtil.isEmpty(path)) {
        continue;
      }
      final String protocol = VirtualFileManager.extractProtocol(path);
      final String url = protocol != null ? path : VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
      container.add(url);
    }
  }

  public Set<VirtualFile> getAddedPathFiles() {
    return getPathsAsVirtualFiles(myAddedPaths);
  }

  public Set<VirtualFile> getExcludedPathFiles() {
    return getPathsAsVirtualFiles(myExcludedPaths);
  }

  private static Set<VirtualFile> getPathsAsVirtualFiles(VirtualFilePointerContainer paths) {
    Set<VirtualFile> ret = new HashSet<>();
    Collections.addAll(ret, paths.getFiles());
    return ret;
  }
}
