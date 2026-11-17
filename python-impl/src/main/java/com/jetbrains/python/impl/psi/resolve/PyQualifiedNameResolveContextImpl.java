// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The only {@link PyQualifiedNameResolveContext}.
 * <p>
 * {@code interpreter} is set only for a context that has no module and no foothold, see {@link PyResolveImportUtil#fromInterpreter}.
 * Resolve then visits the roots of that interpreter. A context with a module or a foothold finds its interpreter from them.
 */
public final class PyQualifiedNameResolveContextImpl implements PyQualifiedNameResolveContext {
  private final PsiManager psiManager;
  private final @Nullable Module module;
  private final @Nullable PsiElement foothold;
  final @Nullable Sdk interpreter;
  private final int relativeLevel;
  private final boolean withoutRoots;
  private final boolean withoutForeign;
  // withMembers means that re-imports from __init__.py file will be handled correctly.
  private final boolean withMembers;
  private final boolean withPlainDirectories;
  private final boolean withoutStubs;

  public PyQualifiedNameResolveContextImpl(PsiManager psiManager, @Nullable Module module, @Nullable PsiElement foothold) {
    this(psiManager, module, foothold, null);
  }

  public PyQualifiedNameResolveContextImpl(PsiManager psiManager,
                                           @Nullable Module module,
                                           @Nullable PsiElement foothold,
                                           @Nullable Sdk interpreter) {
    this(psiManager, module, foothold, interpreter, -1, false, false, false, false, false);
  }

  public PyQualifiedNameResolveContextImpl(PsiManager psiManager,
                                           @Nullable Module module,
                                           @Nullable PsiElement foothold,
                                           @Nullable Sdk interpreter,
                                           int relativeLevel,
                                           boolean withoutRoots,
                                           boolean withoutForeign,
                                           boolean withMembers,
                                           boolean withPlainDirectories,
                                           boolean withoutStubs) {
    this.psiManager = psiManager;
    this.module = module;
    this.foothold = foothold;
    this.interpreter = interpreter;
    this.relativeLevel = relativeLevel;
    this.withoutRoots = withoutRoots;
    this.withoutForeign = withoutForeign;
    this.withMembers = withMembers;
    this.withPlainDirectories = withPlainDirectories;
    this.withoutStubs = withoutStubs;
  }

  @Override
  public @Nullable PsiElement getFoothold() {
    return foothold;
  }

  @Override
  public int getRelativeLevel() {
    return relativeLevel;
  }

  @Override
  public @Nullable Module getModule() {
    return module;
  }

  @Override
  public Project getProject() {
    return psiManager.getProject();
  }

  @Override
  public boolean getWithoutRoots() {
    return withoutRoots;
  }

  @Override
  public boolean getWithoutForeign() {
    return withoutForeign;
  }

  @Override
  public PsiManager getPsiManager() {
    return psiManager;
  }

  @Override
  public boolean getWithMembers() {
    return withMembers;
  }

  @Override
  public boolean getWithPlainDirectories() {
    return withPlainDirectories;
  }

  @Override
  public boolean getWithoutStubs() {
    return withoutStubs;
  }

  @Override
  public boolean isValid() {
    PsiFile footholdFile = getFootholdFile();
    return footholdFile == null || footholdFile.isValid();
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithoutForeign() {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, withoutRoots, true, withMembers,
                                                 withPlainDirectories, withoutStubs);
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithMembers() {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, withoutRoots, withoutForeign, true,
                                                 withPlainDirectories, withoutStubs);
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithPlainDirectories() {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, withoutRoots, withoutForeign,
                                                 withMembers, true, withoutStubs);
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithRelative(int relativeLevel) {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, withoutRoots, withoutForeign,
                                                 withMembers, withPlainDirectories, withoutStubs);
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithoutRoots() {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, true, withoutForeign, withMembers,
                                                 withPlainDirectories, withoutStubs);
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithRoots() {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, false, withoutForeign, withMembers,
                                                 withPlainDirectories, withoutStubs);
  }

  @Override
  public PyQualifiedNameResolveContextImpl copyWithoutStubs() {
    return new PyQualifiedNameResolveContextImpl(psiManager, module, foothold, interpreter, relativeLevel, withoutRoots, withoutForeign,
                                                 withMembers, withPlainDirectories, true);
  }

  @Override
  public @Nullable PsiDirectory getContainingDirectory() {
    PsiFile file = getFootholdFile();
    if (file == null) {
      return null;
    }
    return relativeLevel > 0 ? ResolveImportUtil.stepBackFrom(file, relativeLevel) : file.getContainingDirectory();
  }

  @Override
  public @Nullable PsiFile getFootholdFile() {
    if (foothold instanceof PsiDirectory directory) {
      return directory.findFile(PyNames.INIT_DOT_PY);
    }
    if (foothold == null) {
      return null;
    }
    PsiFile containingFile = foothold.getContainingFile();
    return containingFile != null ? containingFile.getOriginalFile() : null;
  }

  @Override
  public boolean getVisitAllModules() {
    PsiElement file = foothold;
    return file != null && (PythonRuntimeService.getInstance().isInPydevConsole(file) ||
                            PythonRuntimeService.getInstance().isInScratchFile(file) ||
                            file instanceof PyExpressionCodeFragmentImpl);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PyQualifiedNameResolveContextImpl that)) return false;
    return relativeLevel == that.relativeLevel &&
           withoutRoots == that.withoutRoots &&
           withoutForeign == that.withoutForeign &&
           withMembers == that.withMembers &&
           withPlainDirectories == that.withPlainDirectories &&
           withoutStubs == that.withoutStubs &&
           psiManager.equals(that.psiManager) &&
           Objects.equals(module, that.module) &&
           Objects.equals(foothold, that.foothold) &&
           Objects.equals(interpreter, that.interpreter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(psiManager, module, foothold, interpreter, relativeLevel, withoutRoots, withoutForeign, withMembers,
                        withPlainDirectories, withoutStubs);
  }

  @Override
  public String toString() {
    return "PyQualifiedNameResolveContextImpl(psiManager=" + psiManager +
           ", module=" + module +
           ", foothold=" + foothold +
           ", interpreter=" + interpreter +
           ", relativeLevel=" + relativeLevel +
           ", withoutRoots=" + withoutRoots +
           ", withoutForeign=" + withoutForeign +
           ", withMembers=" + withMembers +
           ", withPlainDirectories=" + withPlainDirectories +
           ", withoutStubs=" + withoutStubs + ")";
  }

  /**
   * The interpreter of the Python console that [foothold] is in, or {@code null}. The console stores its SDK, and the registry
   * of its project holds the interpreter of that SDK.
   */
  static @Nullable Sdk consoleInterpreterOf(PsiElement foothold) {
    return PythonRuntimeService.getInstance().getConsoleSdk(foothold);
  }

  /**
   * The interpreter of a context without a module and a foothold, see {@link PyQualifiedNameResolveContextImpl#interpreter}.
   * {@link PyQualifiedNameResolveContextImpl} is the only implementation, so the cast always succeeds.
   */
  public static @Nullable Sdk getStoredInterpreter(PyQualifiedNameResolveContext context) {
    return ((PyQualifiedNameResolveContextImpl)context).interpreter;
  }
}
