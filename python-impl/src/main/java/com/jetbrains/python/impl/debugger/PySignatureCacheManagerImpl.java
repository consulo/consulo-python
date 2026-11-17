// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@ServiceImpl
@Singleton
public final class PySignatureCacheManagerImpl extends PySignatureCacheManager {
  private static final Logger LOG = Logger.getInstance(PySignatureCacheManagerImpl.class.getName());

  private static final boolean SHOULD_OVERWRITE_TYPES = false;

  public static final FileAttribute CALL_SIGNATURES_ATTRIBUTE = new FileAttribute("call.signatures.attribute", 1, true);

  private final Project myProject;

  private final LoadingCache<VirtualFile, String> mySignatureCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build(
      new CacheLoader<>() {
        @Override
        public String load(VirtualFile key) {
          return readAttributeFromFile(key);
        }
      });

  @Inject
  public PySignatureCacheManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void recordSignature(PySignature signature) {
    GlobalSearchScope scope = (GlobalSearchScope)ProjectScopes.getProjectScope(myProject);

    VirtualFile file = getFile(signature);
    if (file != null && ReadAction.compute(() -> scope.contains(file))) {
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

        lines[i] = changeSignatureString(file.getCanonicalPath(), signature, lines[i]);
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

  public static String changeSignatureString(String filePath, PySignature signature, String oldSignatureString) {
    if (SHOULD_OVERWRITE_TYPES) {
      return signatureToString(signature);
    }
    else {
      //noinspection ConstantConditions
      return signatureToString(
        stringToSignature(filePath, oldSignatureString).addAllArgs(signature).addReturnType(signature.getReturnTypeQualifiedName()));
    }
  }

  private void writeAttribute(VirtualFile file, String attrString) {
    String cachedValue = mySignatureCache.asMap().get(file);
    if (!attrString.equals(cachedValue)) {
      mySignatureCache.put(file, attrString);
      writeAttributeToAFile(file, attrString);
    }
  }

  private static void writeAttributeToAFile(VirtualFile file, String attrString) {
    try {
      CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(file, attrString.getBytes(StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      LOG.warn("Can't write attribute " + file.getCanonicalPath() + " " + attrString);
    }
  }

  @Override
  public @Nullable String findParameterType(PyAstFunction function, String name) {
    final PySignature signature = findSignature(function);
    if (signature != null) {
      return signature.getArgTypeQualifiedName(name);
    }
    return null;
  }

  @Override
  public @Nullable PySignature findSignature(PyAstFunction function) {
    VirtualFile file = getFile((PyFunction)function);
    if (file != null) {
      return readSignatureAttributeFromFile(file, getFunctionName((PyFunction)function));
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

  private @Nullable PySignature readSignatureAttributeFromFile(VirtualFile file, String name) {
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

  private @Nullable String readAttribute(VirtualFile file) {
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

  private static String readAttributeFromFile(VirtualFile file) {
    byte[] data;
    try {
      data = CALL_SIGNATURES_ATTRIBUTE.readAttributeBytes(file);
    }
    catch (Exception e) {
      data = null;
    }

    String content;
    if (data != null && data.length > 0) {
      content = new String(data, StandardCharsets.UTF_8);
    }
    else {
      content = null;
    }
    return content != null ? content : "";
  }


  private static @Nullable PySignature stringToSignature(String path, String string) {
    String[] parts = string.split("\t");
    if (parts.length > 0) {
      PySignature signature = new PySignature(path, parts[0]);
      for (int i = 1; i < parts.length; i++) {
        String part = parts[i];
        if (part.isEmpty()) {
          continue;
        }
        String[] var = part.split(":");
        if (var.length == 2) {
          if (RETURN_TYPE.equals(var[0])) {
            signature = signature.addReturnType(var[1]);
          }
          else {
            signature = signature.addArgument(var[0], var[1]);
          }
        }
        else {
          throw new IllegalStateException(
            "Should be <name>:<type> format for arg or " + RETURN_TYPE + ":<type> for return type; '" + part + "' instead.");
        }
      }
      return signature;
    }
    return null;
  }

  private static @Nullable VirtualFile getFile(PySignature signature) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(signature.getFile());
  }

  private static @Nullable VirtualFile getFile(PyFunction function) {
    PsiFile file = function.getContainingFile();

    return file != null ? file.getOriginalFile().getVirtualFile() : null;
  }


  @Override
  public boolean clearCache() {
    final Ref<Boolean> deleted = Ref.create(false);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      (Runnable)() -> ProjectFileIndex.getInstance(myProject).iterateContent(fileOrDir -> {
        if (readAttribute(fileOrDir) != null) {
          writeAttribute(fileOrDir, "");
          deleted.set(true);
        }
        if (ProgressManager.getInstance().getProgressIndicator().isCanceled()) {
          return false;
        }
        return true;
      }), PyLocalize.debuggerCleaningSignatureCache(), true, myProject);
    return deleted.get();
  }
}
