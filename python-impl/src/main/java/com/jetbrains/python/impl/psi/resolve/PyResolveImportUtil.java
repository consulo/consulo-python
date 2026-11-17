// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.google.common.base.Preconditions;
import com.jetbrains.python.impl.codeInsight.typing.PyStubPackages;
import com.jetbrains.python.impl.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.impl.facet.PythonPathContributingFacet;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyThirdPartySdkDetector;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.language.content.FileIndexFacade;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.extension.ModuleExtension;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Python resolve utilities for qualified names.
 * <p>
 * TODO: Merge with ResolveImportUtil, maybe make these functions the methods of PyQualifiedNameResolveContext.
 */
public final class PyResolveImportUtil {
  private PyResolveImportUtil() {
  }

  /**
   * Resolves a qualified [name] to the list of packages and modules according to the [context].
   *
   * @see #resolveTopLevelMember
   */
  @RequiredReadAction
  public static List<PsiElement> resolveQualifiedName(QualifiedName name, PyQualifiedNameResolveContext context) {
    checkAccess();
    if (!context.isValid()) {
      return Collections.emptyList();
    }

    PsiDirectory relativeDirectory = context.getContainingDirectory();
    List<PsiElement> relativeResults = resolveWithRelativeLevel(name, context);
    boolean foundRelativeImport = relativeDirectory != null &&
                                  relativeResults.stream()
                                    .anyMatch(it -> isSameDirectoryOrRelativeImportResult(name, relativeDirectory, it, context));

    PythonPathCache cache = findCache(context);
    boolean mayCache = cache != null && !foundRelativeImport;
    QualifiedName key = cachePrefix(context).append(name);

    if (mayCache) {
      List<PsiElement> cachedResults = cache.get(key);
      if (cachedResults != null) {
        return distinct(plus(relativeResults, cachedResults));
      }
    }

    List<PsiElement> foreignResults = foreignResults(name, context);
    List<PsiElement> pythonResults = new ArrayList<>();
    pythonResults.addAll(relativeResults);
    pythonResults.addAll(resolveModuleFromRoots(name, context));
    pythonResults.addAll(relativeResultsFromSkeletons(name, context));
    pythonResults = distinct(pythonResults);

    List<PsiElement> sameDirectoryPython3Results = new ArrayList<>();
    List<PsiElement> notSameDirectoryPython3Results = new ArrayList<>();
    for (PsiElement it : pythonResults) {
      boolean sameDirectory;
      if (relativeDirectory == null || LanguageLevel.forElement(relativeDirectory).isPython2()) {
        sameDirectory = false;
      }
      else {
        sameDirectory = isSameDirectoryResult(it, context, name);
      }
      (sameDirectory ? sameDirectoryPython3Results : notSameDirectoryPython3Results).add(it);
    }

    List<PsiElement> allResults;
    if (relativeDirectory != null && PyUtil.isExplicitPackage(relativeDirectory)) {
      allResults =
        filterTopPriorityResultsWithFallback(notSameDirectoryPython3Results, sameDirectoryPython3Results, foreignResults, name, context);
    }
    else {
      allResults =
        filterTopPriorityResultsWithFallback(sameDirectoryPython3Results, notSameDirectoryPython3Results, foreignResults, name, context);
    }

    List<PsiElement> results = new ArrayList<>();
    for (PsiElement it : allResults) {
      if (!(it instanceof PsiFileSystemItem && isInSkeletons(it))) {
        results.add(it);
      }
    }
    if (results.isEmpty()) {
      results = allResults;
    }

    if (mayCache) {
      cache.put(key, results);
    }

    return results;
  }

  /**
   * Resolves a qualified [name] to the list of packages and modules with Python semantics according to the [context].
   */
  private static List<PsiElement> resolveModuleFromRoots(QualifiedName name, PyQualifiedNameResolveContext context) {
    QualifiedName head = name.removeTail(name.getComponentCount() - 1);
    QualifiedName nameNoHead = name.removeHead(1);
    List<PsiElement> results = distinct(resultsFromRoots(head, context));
    for (String component : nameNoHead.getComponents()) {
      List<PsiElement> next = new ArrayList<>();
      for (PsiElement it : filterTopPriorityResults(results, context.getModule(), component)) {
        if (it instanceof PsiFileSystemItem item) {
          next.addAll(resolveModuleAt(QualifiedName.fromComponents(component), item, context));
        }
      }
      results = next;
    }
    return results;
  }

  /**
   * Resolves a [name] to the first module member defined at the top-level.
   */
  @RequiredReadAction
  public static @Nullable PsiElement resolveTopLevelMember(QualifiedName name, PyQualifiedNameResolveContext context) {
    checkAccess();
    String memberName = name.getLastComponent();
    if (memberName == null) {
      return null;
    }
    for (PsiElement resolved : resolveQualifiedName(name.removeLastComponent(), context)) {
      PsiElement it = PyUtil.turnDirIntoInit(resolved);
      if (it instanceof PyFile pyFile) {
        for (RatedResolveResult result : pyFile.multiResolveName(memberName)) {
          return result.getElement();
        }
      }
    }
    return null;
  }

  /**
   * Resolves a [name] relative to the specified [item].
   */
  @RequiredReadAction
  public static List<PsiElement> resolveModuleAt(QualifiedName name, @Nullable PsiFileSystemItem item, PyQualifiedNameResolveContext context) {
    checkAccess();
    List<PsiElement> empty = Collections.emptyList();
    if (item == null || !item.isValid()) {
      return empty;
    }
    List<PsiElement> seekers = List.of(item);
    for (String component : name.getComponents()) {
      if (component == null) {
        seekers = empty;
      }
      else {
        List<PsiElement> next = new ArrayList<>();
        for (PsiElement it : seekers) {
          List<RatedResolveResult> children = ResolveImportUtil.resolveChildren(it, component, context.getFootholdFile(),
                                                                        !context.getWithMembers(),
                                                                        !context.getWithPlainDirectories(), context.getWithoutStubs(),
                                                                        context.getWithoutForeign());
          next.addAll(PyUtil.filterTopPriorityElements(children));
        }
        seekers = next;
      }
    }
    return seekers;
  }

  /**
   * Creates a [PyQualifiedNameResolveContext] from a [foothold] element.
   */
  public static PyQualifiedNameResolveContext fromFoothold(PsiElement foothold) {
    PsiFile containingFile = foothold.getContainingFile();
    Module module = ModuleUtilCore.findModuleForPsiElement(containingFile != null ? containingFile : foothold);
    return new PyQualifiedNameResolveContextImpl(foothold.getManager(), module, foothold);
  }

  /**
   * Creates a [PyQualifiedNameResolveContext] from a [module].
   */
  public static PyQualifiedNameResolveContext fromModule(Module module) {
    return new PyQualifiedNameResolveContextImpl(PsiManager.getInstance(module.getProject()), module, null);
  }

  /**
   * Creates a [PyQualifiedNameResolveContext] that resolves in the roots of [interpreter].
   */
  public static PyQualifiedNameResolveContext fromInterpreter(Project project, Sdk interpreter) {
    return new PyQualifiedNameResolveContextImpl(PsiManager.getInstance(project), null, null, interpreter);
  }

  private static QualifiedName cachePrefix(PyQualifiedNameResolveContext context) {
    List<String> results = new ArrayList<>();
    if (context.getWithoutStubs()) {
      results.add("without-stubs");
    }
    if (context.getWithoutForeign()) {
      results.add("without-foreign");
    }
    if (context.getWithoutRoots()) {
      results.add("without-roots");
    }
    if (context.getWithMembers()) {
      results.add("with-members");
    }
    return QualifiedName.fromComponents(results);
  }

  private static List<PsiElement> foreignResults(QualifiedName name, PyQualifiedNameResolveContext context) {
    if (context.getWithoutForeign()) {
      return Collections.emptyList();
    }
    List<PsiElement> results = new ArrayList<>();
    for (PyImportResolver it : PyImportResolver.EP_NAME.getExtensionList()) {
      PsiElement result = DumbService.getInstance(context.getProject()).computeWithAlternativeResolveEnabled(
        () -> it.resolveImportReference(name, context, !context.getWithoutRoots())
      );
      if (result != null) {
        results.add(result);
      }
    }
    return results;
  }

  private static List<PsiElement> relativeResultsFromSkeletons(QualifiedName name, PyQualifiedNameResolveContext context) {
    PsiFile footholdFile = context.getFootholdFile();
    if (context.getWithoutRoots() && footholdFile != null) {
      VirtualFile virtualFile = footholdFile.getVirtualFile();
      if (virtualFile == null || FileIndexFacade.getInstance(context.getProject()).isInContent(virtualFile)) {
        return Collections.emptyList();
      }
      PsiDirectory containingDirectory = context.getContainingDirectory();
      if (containingDirectory != null) {
        QualifiedName containingName = QualifiedNameFinder.findCanonicalImportPath(containingDirectory, null);
        if (containingName != null && containingName.getComponentCount() > 0) {
          QualifiedName absoluteName = containingName.append(name);
          Module footholdModule = ModuleUtilCore.findModuleForPsiElement(footholdFile);
          Sdk interpreter = footholdModule != null ? MainPythonSdkKt.pythonSdkIfReady(footholdModule) : null;
          if (interpreter == null) {
            return Collections.emptyList();
          }
          VirtualFile skeletonsVirtualFile = PySdkUtil.findSkeletonsDir(interpreter);
          if (skeletonsVirtualFile == null) {
            return Collections.emptyList();
          }
          PsiDirectory skeletonsDir = context.getPsiManager().findDirectory(skeletonsVirtualFile);
          return resolveModuleAt(absoluteName, skeletonsDir, context.copyWithoutForeign());
        }
      }
    }
    return Collections.emptyList();
  }

  public static List<PsiElement> relativeResultsForStubsFromRoots(QualifiedName name, PyQualifiedNameResolveContext context) {
    if (!(context.getFootholdFile() instanceof PyiFile) || context.getRelativeLevel() <= 0) {
      return Collections.emptyList();
    }
    PsiDirectory containingDirectory = context.getContainingDirectory();
    if (containingDirectory == null) {
      return Collections.emptyList();
    }
    QualifiedName containingName = QualifiedNameFinder.findCanonicalImportPath(containingDirectory, null);
    if (containingName == null) {
      return Collections.emptyList();
    }
    if (containingName.getComponentCount() <= 0) {
      return Collections.emptyList();
    }
    QualifiedName absoluteName = containingName.append(name);
    return resultsFromRoots(absoluteName, context.copyWithRelative(-1).copyWithRoots());
  }

  private static List<PsiElement> resolveWithRelativeLevel(QualifiedName name, PyQualifiedNameResolveContext context) {
    PsiFile footholdFile = context.getFootholdFile();
    if (context.getRelativeLevel() >= 0 && footholdFile != null) {
      return plus(resolveModuleAt(name, context.getContainingDirectory(), context), relativeResultsForStubsFromRoots(name, context));
    }
    return Collections.emptyList();
  }

  /**
   * Collects resolve results from all roots available.
   * <p>
   * The retuning {@code List<PsiElement>} contains all elements {@code name} references
   * and does not take into account root order.
   */
  private static List<PsiElement> resultsFromRoots(QualifiedName name, PyQualifiedNameResolveContext context) {
    if (context.getWithoutRoots()) {
      return Collections.emptyList();
    }

    List<PsiElement> moduleResults = new ArrayList<>();
    List<PsiElement> sdkResults = new ArrayList<>();

    Module module = context.getModule();
    PsiFile footholdFile = context.getFootholdFile();
    boolean withoutStubs = context.getWithoutStubs();
    Sdk storedInterpreter = PyQualifiedNameResolveContextImpl.getStoredInterpreter(context);
    PsiElement foothold = context.getFoothold();
    Sdk consoleInterpreter = foothold != null && context.getVisitAllModules()
                             ? PyQualifiedNameResolveContextImpl.consoleInterpreterOf(foothold)
                             : null;
    // The interpreter whose language level picks the typeshed stubs of the standard library: the console interpreter
    // for a context that visits all modules, otherwise the interpreter of the context or of its module.
    Sdk languageInterpreter;
    if (context.getVisitAllModules()) {
      languageInterpreter = consoleInterpreter;
    }
    else if (storedInterpreter != null) {
      languageInterpreter = storedInterpreter;
    }
    else {
      languageInterpreter = module != null ? MainPythonSdkKt.pythonSdkIfReady(module) : null;
    }
    LanguageLevel languageLevel = languageInterpreter != null ? PythonSdkType.getLanguageLevelForSdk(languageInterpreter) : null;

    RootVisitor visitor = (root, rootModule, sdk, isModuleSource) -> {
      List<PsiElement> results = isModuleSource ? moduleResults : sdkResults;
      if (!root.isValid() ||
          languageLevel != null && PyTypeShed.INSTANCE.isInside(root) && !PyTypeShed.INSTANCE.maySearchForStubInRoot(name, root, languageLevel)
      ) {
        return true;
      }
      if (withoutStubs) {
        if (PyTypeShed.INSTANCE.isInside(root)) {
          return true;
        }
        PsiDirectory rootDirectory = PsiManager.getInstance(context.getProject()).findDirectory(root);
        if (rootDirectory != null && PyStubPackages.isInStubPackage(rootDirectory)) {
          return true;
        }
      }
      results.addAll(resolveInRoot(name, root, context));
      if (isAcceptRootAsTopLevelPackage(context) && name.matchesPrefix(QualifiedName.fromDottedString(root.getName()))) {
        //TODO[akniazev]: resolving from parent root. Is it still a thing for Django?
        VirtualFile parent = root.getParent();
        if (parent != null) {
          results.addAll(resolveInRoot(name, parent, context));
        }
      }
      return true;
    };

    if (context.getVisitAllModules()) {
      RootVisitorHost.visitRootsInAllModules(context.getProject(), visitor);
      if (consoleInterpreter != null) {
        RootVisitorHost.visitInterpreterRoots(context.getProject(), consoleInterpreter, visitor);
      }
      else if (footholdFile != null) {
        RootVisitorHost.visitSdkRoots(footholdFile, visitor);
      }
    }
    else if (module != null) {
      RootVisitorHost.visitRoots(module, false, visitor);
    }
    else if (footholdFile != null) {
      RootVisitorHost.visitRoots(footholdFile, visitor);
    }
    else if (storedInterpreter != null) {
      RootVisitorHost.visitInterpreterRoots(context.getProject(), storedInterpreter, visitor);
    }
    else {
      throw new IllegalStateException();
    }
    return plus(moduleResults, sdkResults);
  }

  private static boolean isAcceptRootAsTopLevelPackage(PyQualifiedNameResolveContext context) {
    Module module = context.getModule();
    if (module != null) {
      boolean result = false;
      // PyModuleService.forAllFacets: in Consulo the facets of a module are its extensions
      for (ModuleExtension<?> it : ModuleRootManager.getInstance(module).getExtensions()) {
        if (it instanceof PythonPathContributingFacet facet && facet.acceptRootAsTopLevelPackage()) {
          result = true;
        }
      }
      return result;
    }
    return false;
  }

  public static List<PsiElement> resolveInRoot(QualifiedName name, VirtualFile root, PyQualifiedNameResolveContext context) {
    if (root.isDirectory()) {
      return resolveModuleAt(name, context.getPsiManager().findDirectory(root), context);
    }
    else {
      return Collections.emptyList();
    }
  }

  public static @Nullable PythonPathCache findCache(PyQualifiedNameResolveContext context) {
    if (context.getVisitAllModules()) {
      return null;
    }
    Module module = context.getModule();
    if (module != null) {
      return PythonModulePathCache.getInstance(module);
    }
    PsiFile footholdFile = context.getFootholdFile();
    if (footholdFile != null) {
      Sdk interpreter = PyBuiltinCache.findInterpreterForNonModuleFile(footholdFile);
      return interpreter != null ? PythonSdkPathCache.getInstance(context.getProject(), interpreter) : null;
    }
    return null;
  }

  private static boolean isSameDirectoryResult(PsiElement element, PyQualifiedNameResolveContext context, QualifiedName name) {
    if (context.getRelativeLevel() != 0) return false;
    boolean sameDirectoryImportsEnabled = !ResolveImportUtil.isAbsoluteImportEnabledFor(context.getFoothold());
    if (!sameDirectoryImportsEnabled || !(element instanceof PsiFileSystemItem fileSystemItem)) return false;
    QualifiedName shortestImportableQName = QualifiedNameFinder.findShortestImportableQName(fileSystemItem);
    if (shortestImportableQName != null) {
      return !name.equals(shortestImportableQName);
    }
    else {
      PsiDirectory footholdDir = context.getContainingDirectory();
      if (footholdDir == null) return false;
      return PsiTreeUtil.isAncestor(footholdDir, element, true);
    }
  }

  private static boolean isSameDirectoryOrRelativeImportResult(QualifiedName name, PsiDirectory directory, PsiElement result,
                                                               PyQualifiedNameResolveContext context) {
    if (context.getRelativeLevel() > 0) {
      return true;
    }
    else {
      return PyUtil.isPackage(directory, false, null) && isSameDirectoryResult(result, context, name);
    }
  }

  private static void checkAccess() {
    Preconditions.checkState(Application.get().isReadAccessAllowed(), "This method requires read access");
  }

  private static List<PsiElement> filterTopPriorityResultsWithFallback(List<PsiElement> primaryResults,
                                                                       List<PsiElement> fallbackResults,
                                                                       List<PsiElement> foreignResults,
                                                                       QualifiedName name,
                                                                       PyQualifiedNameResolveContext context) {
    List<PsiElement> allResults = new ArrayList<>(primaryResults);
    allResults.addAll(fallbackResults);
    allResults.addAll(foreignResults);
    if (name.getComponentCount() <= 0) return allResults;
    List<PsiElement> filteredPrimaryResults = filterTopPriorityResults(primaryResults, context.getModule());
    if (!filteredPrimaryResults.isEmpty()) return plus(filteredPrimaryResults, foreignResults);
    return plus(filterTopPriorityResults(fallbackResults, context.getModule()), foreignResults);
  }

  private static List<PsiElement> filterTopPriorityResults(List<PsiElement> resolved, @Nullable Module module) {
    return filterTopPriorityResults(resolved, module, null);
  }

  /**
   * Filters resolved elements according to their import priority in sys.path and
   * <a href="https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order">PEP 561</a> rules.
   */
  private static List<PsiElement> filterTopPriorityResults(List<PsiElement> resolved, @Nullable Module module, @Nullable String nextComponent) {
    if (resolved.isEmpty()) return Collections.emptyList();

    SortedMap<Priority, List<PsiElement>> groupedResults = new TreeMap<>();
    for (PsiElement it : resolved) {
      groupedResults.computeIfAbsent(resolvedElementPriority(it, module), k -> new ArrayList<>()).add(it);
    }
    List<PsiElement> skeletons = groupedResults.remove(Priority.SKELETON);
    if (skeletons == null) {
      skeletons = Collections.emptyList();
    }

    if (topResultIs(groupedResults, Priority.NAMESPACE_PACKAGE)) {
      return plus(Objects.requireNonNull(groupedResults.get(Priority.NAMESPACE_PACKAGE)), skeletons);
    }
    groupedResults.remove(Priority.NAMESPACE_PACKAGE);

    Priority topPriority = groupedResults.isEmpty() ? null : groupedResults.firstKey();
    PsiElement topResult = null;
    if (topPriority != null) {
      List<PsiElement> topList = groupedResults.get(topPriority);
      topResult = topList != null && !topList.isEmpty() ? topList.get(0) : null;
    }
    List<PsiElement> priorityResults;
    if (groupedResults.isEmpty()) {
      priorityResults = Collections.emptyList();
    }
    else if (topPriority != null && topResult != null && shouldFallbackToNextPriority(topPriority, topResult, module, nextComponent)) {
      priorityResults = firstResultWithFallback(groupedResults, topPriority);
    }
    else {
      priorityResults = List.of(groupedResults.values().iterator().next().get(0));
    }
    return plus(priorityResults, skeletons);
  }

  private static boolean topResultIs(SortedMap<Priority, List<PsiElement>> map, Priority priority) {
    return map.containsKey(priority) && map.headMap(priority).isEmpty();
  }

  private static List<PsiElement> firstResultWithFallback(SortedMap<Priority, List<PsiElement>> results, Priority priority) {
    PsiElement first = Objects.requireNonNull(results.get(priority)).get(0);
    PsiElement nextByPriority = null;
    boolean skipped = false;
    for (Map.Entry<Priority, List<PsiElement>> entry : results.tailMap(priority).entrySet()) {
      if (!skipped) {
        skipped = true;
        continue;
      }
      List<PsiElement> values = entry.getValue();
      if (!values.isEmpty()) {
        nextByPriority = values.get(0);
      }
      break;
    }

    List<PsiElement> result = new ArrayList<>(2);
    result.add(first);
    if (nextByPriority != null) {
      result.add(nextByPriority);
    }
    return result;
  }

  private static boolean shouldFallbackToNextPriority(Priority priority, PsiElement element, @Nullable Module module, @Nullable String nextComponent) {
    return switch (priority) {
      case USER_STUB -> isUserFile(element, module) && isInPartialStub(element, nextComponent);
      case STUB_PACKAGE -> isInPartialStub(element, nextComponent);
      case TYPESHED -> PyTypeShed.INSTANCE.isInPartialThirdPartyStubPackage(element);
      case THIRD_PARTY_SDK -> true;
      default -> false;
    };
  }

  /**
   * A stub package result must keep the runtime package as a fallback when the stub does not authoritatively cover the
   * path being resolved: either the enclosing stub package is marked partial, or the next component to resolve is a
   * namespace package inside the stub (namespace packages are inherently incomplete, see PEP 561).
   */
  private static boolean isInPartialStub(PsiElement element, @Nullable String nextComponent) {
    return PyStubPackages.isInPartialStubPackage(element) ||
           (nextComponent != null && PyStubPackages.hasNamespaceSubPackage(element, nextComponent));
  }

  /**
   * See <a href="https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order">PEP 561</a>.
   */
  private static Priority resolvedElementPriority(PsiElement element, @Nullable Module module) {
    if (PyNamespacePackageUtil.isNamespacePackage(element)) return Priority.NAMESPACE_PACKAGE;
    if (isUserFile(element, module)) return PyiUtil.isPyiFileOfPackage(element) ? Priority.USER_STUB : Priority.USER_CODE;
    if (PyStubPackages.isInStubPackage(element)) return Priority.STUB_PACKAGE;
    if (isInTypeShed(element)) return Priority.TYPESHED;
    if (isInSkeletons(element)) return Priority.SKELETON;
    if (PyiUtil.isPyiFileOfPackage(element)) return Priority.PROVIDED_STUB;
    if (PyStubPackages.isInInlinePackage(element, module)) return Priority.INLINE_PACKAGE;
    if (isInProvidedSdk(element)) return Priority.THIRD_PARTY_SDK;
    return Priority.OTHER;
  }

  public static boolean isInSkeletons(PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    Sdk interpreter = module != null ? MainPythonSdkKt.pythonSdkIfReady(module) : null;
    if (interpreter == null) return false;
    VirtualFile vFile;
    if (element instanceof PsiDirectory directory) {
      vFile = directory.getVirtualFile();
    }
    else {
      PsiFile containingFile = element.getContainingFile();
      vFile = containingFile != null ? containingFile.getVirtualFile() : null;
    }
    if (vFile == null) return false;
    VirtualFile skeletons = PySdkUtil.findSkeletonsDir(interpreter);
    if (skeletons == null) return false;
    return VirtualFileUtil.isAncestor(skeletons, vFile, false);
  }

  private static boolean isInProvidedSdk(PsiElement element) {
    return PyThirdPartySdkDetector.EP_NAME.findFirstSafe(it -> it.isInThirdPartySdk(element)) != null;
  }

  private static boolean isUserFile(PsiElement element, @Nullable Module module) {
    if (module == null || !(element instanceof PsiFileSystemItem fileSystemItem)) return false;
    VirtualFile virtualFile = fileSystemItem.getVirtualFile();
    if (virtualFile == null) return false;
    return ModuleUtilCore.moduleContainsFile(module, virtualFile, false);
  }

  private static boolean isInTypeShed(PsiElement element) {
    if (!PyiUtil.isPyiFileOfPackage(element)) return false;
    VirtualFile virtualFile = element instanceof PsiFileSystemItem fileSystemItem ? fileSystemItem.getVirtualFile() : null;
    if (virtualFile == null) return false;
    return PyTypeShed.INSTANCE.isInside(virtualFile);
  }

  private static List<PsiElement> plus(List<PsiElement> first, List<PsiElement> second) {
    List<PsiElement> result = new ArrayList<>(first.size() + second.size());
    result.addAll(first);
    result.addAll(second);
    return result;
  }

  private static List<PsiElement> distinct(List<PsiElement> elements) {
    return new ArrayList<>(new LinkedHashSet<>(elements));
  }

  /**
   * See <a href="https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order">PEP 561</a>.
   * Order is important, see {@link #filterTopPriorityResults}.
   */
  private enum Priority {
    USER_STUB, // pyi file located in user's project
    USER_CODE, // py file located in user's project
    STUB_PACKAGE, // pyi file located in some stub package
    PROVIDED_STUB, // pyi file provided with installed lib and located inside it
    INLINE_PACKAGE, // py file located in some inline package
    TYPESHED, // pyi file located in typeshed
    THIRD_PARTY_SDK, // project-specific sdk, e.g Google App Engine one
    OTHER, // other cases, e.g. py file located inside installed lib
    NAMESPACE_PACKAGE, // namespace package but may contain several entries in resolve result
    SKELETON // generated skeletons have lowest priority but are always included in the resolve result as a fallback
  }
}
