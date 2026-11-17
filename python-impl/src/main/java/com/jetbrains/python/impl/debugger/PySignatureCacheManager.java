// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.debugger.PySignature;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// TODO consulo: JB keeps this class in python-syntax-core as com.jetbrains.python.debugger.PySignatureCacheManager;
// Consulo keeps it in python-impl because package com.jetbrains.python.debugger belongs to python-debugger
@ServiceAPI(ComponentScope.PROJECT)
public abstract class PySignatureCacheManager {
  public static final String RETURN_TYPE = "<RETURN_TYPE>";

  public static PySignatureCacheManager getInstance(Project project) {
    return project.getInstance(PySignatureCacheManager.class);
  }

  public static String signatureToString(PySignature signature) {
    return signature.getFunctionName() + "\t" + StringUtil.join(arguments(signature), "\t") +
           (signature.getReturnType() != null
            ? "\t" + StringUtil.join(
             signature.getReturnType().getTypesList().stream().map(s -> RETURN_TYPE + ":" + s).collect(Collectors.toList()), "\t") : "");
  }

  private static List<String> arguments(PySignature signature) {
    List<String> res = new ArrayList<>();
    for (PySignature.NamedParameter param : signature.getArgs()) {
      res.add(param.getName() + ":" + param.getTypeQualifiedName());
    }
    return res;
  }

  public abstract void recordSignature(PySignature signature);

  public abstract @Nullable String findParameterType(PyAstFunction function, String name);

  public abstract @Nullable PySignature findSignature(PyAstFunction function);

  public abstract boolean clearCache();
}
