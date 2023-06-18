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

package com.jetbrains.python.impl.debugger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.application.progress.ProgressManager;
import consulo.content.ContentIterator;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author traff
 */
public class PySignatureCacheManagerImpl extends PySignatureCacheManager {
  protected static final Logger LOG = Logger.getInstance(PySignatureCacheManagerImpl.class);

  private final static boolean SHOULD_OVERWRITE_TYPES = false;

  public static final FileAttribute CALL_SIGNATURES_ATTRIBUTE = new FileAttribute("call.signatures.attribute", 1, true);

  private final Project myProject;

  private final LoadingCache<VirtualFile, String> mySignatureCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build(
      new CacheLoader<VirtualFile, String>() {
        @Override
        public String load(VirtualFile key) throws Exception {
          return readAttributeFromFile(key);
        }
      });

  public PySignatureCacheManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void recordSignature(@Nonnull PySignature signature) {
    GlobalSearchScope scope = (GlobalSearchScope) ProjectScopes.getProjectScope(myProject);

    VirtualFile file = getFile(signature);
    if (file != null && scope.contains(file)) {
      recordSignature(file, signature);
    }
  }

  private void recordSignature(VirtualFile file, PySignature signature) {
    String dataString = readAttribute(file);

    String[] lines;
    if (dataString != null) {
      lines = dataString.split("\n");
    }
    else {
      lines = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    boolean found = false;
    int i = 0;
    for (String sign : lines) {
      String[] parts = sign.split("\t");
      if (parts.length > 0 && parts[0].equals(signature.getFunctionName())) {
        found = true;
        if (SHOULD_OVERWRITE_TYPES) {
          lines[i] = signatureToString(signature);
        }
        else {
          //noinspection ConstantConditions
          lines[i] = signatureToString(stringToSignature(file.
            getCanonicalPath(), lines[i]).addAllArgs(signature));
        }
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      System.arraycopy(lines, 0, lines2, 0, lines.length);

      lines2[lines2.length - 1] = signatureToString(signature);
      lines = lines2;
    }

    String attrString = StringUtil.join(lines, "\n");

    writeAttribute(file, attrString);
  }

  private void writeAttribute(@Nonnull VirtualFile file, @Nonnull String attrString) {
    String cachedValue = mySignatureCache.asMap().get(file);
    if (!attrString.equals(cachedValue)) {
      mySignatureCache.put(file, attrString);
      writeAttributeToAFile(file, attrString);
    }
  }

  private static void writeAttributeToAFile(@Nonnull VirtualFile file, @Nonnull String attrString) {
    try {
      CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(file, attrString.getBytes());
    }
    catch (IOException e) {
      LOG.warn("Can't write attribute " + file.getCanonicalPath() + " " + attrString);
    }
  }

  private static String signatureToString(PySignature signature) {
    return signature.getFunctionName() + "\t" + StringUtil.join(arguments(signature), "\t");
  }

  private static List<String> arguments(PySignature signature) {
    List<String> res = Lists.newArrayList();
    for (PySignature.NamedParameter param : signature.getArgs()) {
      res.add(param.getName() + ":" + param.getTypeQualifiedName());
    }
    return res;
  }

  @Nullable
  public String findParameterType(@Nonnull PyFunction function, @Nonnull String name) {
    final PySignature signature = findSignature(function);
    if (signature != null) {
      return signature.getArgTypeQualifiedName(name);
    }
    return null;
  }

  @Nullable
  public PySignature findSignature(@Nonnull PyFunction function) {
    VirtualFile file = getFile(function);
    if (file != null) {
      return readSignatureAttributeFromFile(file, getFunctionName(function));
    }
    else {
      return null;
    }
  }

  private static String getFunctionName(PyFunction function) {
    String name = function.getName();
    if (name == null) {
      return "";
    }

    PyClass cls = function.getContainingClass();

    if (cls != null) {
      name = cls.getName() + "." + name;
    }

    return name;
  }

  @Nullable
  private PySignature readSignatureAttributeFromFile(@Nonnull VirtualFile file, @Nonnull String name) {
    String content = readAttribute(file);

    if (content != null) {
      String[] lines = content.split("\n");
      for (String sign : lines) {
        String[] parts = sign.split("\t");
        if (parts.length > 0 && parts[0].equals(name)) {
          return stringToSignature(file.getCanonicalPath(), sign);
        }
      }
    }

    return null;
  }

  @Nullable
  private String readAttribute(@Nonnull VirtualFile file) {
    try {
      String attrContent = mySignatureCache.get(file);
      if (!StringUtil.isEmpty(attrContent)) {
        return attrContent;
      }
    }
    catch (ExecutionException e) {
      //pass
    }
    return null;
  }

  @Nonnull
  private static String readAttributeFromFile(@Nonnull VirtualFile file) {
    byte[] data;
    try {
      data = CALL_SIGNATURES_ATTRIBUTE.readAttributeBytes(file);
    }
    catch (Exception e) {
      data = null;
    }

    String content;
    if (data != null && data.length > 0) {
      content = new String(data);
    }
    else {
      content = null;
    }
    return content != null ? content : "";
  }


  @Nullable
  private static PySignature stringToSignature(String path, String string) {
    String[] parts = string.split("\t");
    if (parts.length > 0) {
      PySignature signature = new PySignature(path, parts[0]);
      for (int i = 1; i < parts.length; i++) {
        String[] var = parts[i].split(":");
        if (var.length == 2) {
          signature = signature.addArgument(var[0], var[1]);
        }
        else {
          throw new IllegalStateException("Should be <name>:<type> format. " + parts[i] + " instead.");
        }
      }
      return signature;
    }
    return null;
  }

  @Nullable
  private static VirtualFile getFile(@Nonnull PySignature signature) {
    return LocalFileSystem.getInstance().findFileByPath(signature.getFile());
  }

  @Nullable
  private static VirtualFile getFile(@Nonnull PyFunction function) {
    PsiFile file = function.getContainingFile();

    return file != null ? file.getOriginalFile().getVirtualFile() : null;
  }


  @Override
  public void clearCache() {
    final Ref<Boolean> deleted = Ref.create(false);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {

      @Override
      public void run() {
        ProjectFileIndex.SERVICE.getInstance(myProject).iterateContent(new ContentIterator() {
          @Override
          public boolean processFile(VirtualFile fileOrDir) {
            if (readAttribute(fileOrDir) != null) {
              writeAttribute(fileOrDir, "");
              deleted.set(true);
            }
            if (ProgressManager.getInstance().getProgressIndicator().isCanceled()) {
              return false;
            }
            return true;
          }
        });
      }
    }, "Cleaning the cache of dynamically collected types", true, myProject);


    String message;
    if (deleted.get()) {
      message = "Collected signatures were deleted";
    }
    else {
      message = "Nothing to delete";
    }
    Messages.showInfoMessage(myProject, message, "Delete Cache");
  }
}
