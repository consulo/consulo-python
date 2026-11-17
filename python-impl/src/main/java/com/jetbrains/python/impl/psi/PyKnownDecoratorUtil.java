// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecoratable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Contains list of well-behaved decorators from Pythons standard library, that don't change
 * signature of underlying function/class or use it implicitly somewhere (e.g. register as a callback).
 *
 * @author Mikhail Golubev
 */
public final class PyKnownDecoratorUtil {
  private PyKnownDecoratorUtil() {
  }

  /**
   * Returns the known decorators of [element], one entry per decorator in declaration order.
   *
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   */
  public static PyElementKnownDecorators knownDecoratorsOf(PyDecoratable element, TypeEvalContext context) {
    PyDecoratorList decoratorList = element.getDecoratorList();
    if (decoratorList == null) return PyElementKnownDecorators.NONE;
    List<PyKnownDecorators> decorators = new ArrayList<>();
    for (PyDecorator it : decoratorList.getDecorators()) {
      decorators.add(knownDecoratorsOf(it, context));
    }
    return PyElementKnownDecorators.of(decorators);
  }

  /**
   * @deprecated Use knownDecoratorsOf(element, context). The returned list flattens two axes - which decorator, and which
   * known decorator each can resolve to - and holds candidates, not facts: aggregate it with any, never with all.
   */
  @Deprecated
  public static List<PyKnownDecorator> getKnownDecorators(PyDecoratable element, TypeEvalContext context) {
    return knownDecoratorsOf(element, context).asList();
  }

  /**
   * Returns the known decorators that [decorator] can resolve to.
   *
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   */
  public static PyKnownDecorators knownDecoratorsOf(PyDecorator decorator, TypeEvalContext context) {
    QualifiedName qualifiedName = decorator.getQualifiedName();
    if (qualifiedName == null) return PyKnownDecorators.NONE;
    // Avoid resolving property accessor decorators to prevent an infinite recursion
    String lastComponent = qualifiedName.getLastComponent();
    if (PyNames.GETTER.equals(lastComponent) || PyNames.SETTER.equals(lastComponent) || PyNames.DELETER.equals(lastComponent)) {
      return PyKnownDecorators.NONE;
    }
    PsiFile containingFile = decorator.getContainingFile();
    if (!context.maySwitchToAST(decorator)) {
      // Without the AST, the imports of the file give the qualified name of the decorator. This reads stubs only.
      if (!(containingFile instanceof PyFile pyFile)) return PyKnownDecorators.NONE;
      return PyKnownDecorators.of(asKnownDecoratorsInFile(qualifiedName, pyFile));
    }
    List<? extends PsiElement> resolved;
    if (containingFile instanceof PyiFile pyiFile) {
      // In .pyi files it's safe to resolve decorators such as "@overload" flow-insensitively.
      resolved = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, pyiFile, context);
    }
    else {
      resolved = PyUtil.multiResolveTopPriority(Objects.requireNonNull(decorator.getCallee()), PyResolveContext.defaultContext(context));
    }
    List<PyKnownDecorator> candidates = new ArrayList<>();
    for (PsiElement element : resolved) {
      if (!(element instanceof PyQualifiedNameOwner qualifiedNameOwner)) continue;
      String qName = qualifiedNameOwner.getQualifiedName();
      if (qName == null) continue;
      String unqualified = PyNames.FQN.unqualifyBuiltinName(qName);
      PyKnownDecorator known = findByQualifiedName(QualifiedName.fromDottedString(Objects.requireNonNull(unqualified)));
      if (known != null) {
        candidates.add(known);
      }
    }
    return PyKnownDecorators.of(candidates);
  }

  /**
   * @deprecated Use knownDecoratorsOf(decorator, context). The returned list holds resolve candidates, not facts:
   * aggregate it with any, never with all. The returned list is immutable.
   */
  @Deprecated
  public static List<PyKnownDecorator> asKnownDecorators(PyDecorator decorator, TypeEvalContext context) {
    return knownDecoratorsOf(decorator, context).asList();
  }

  /**
   * Returns the known decorators that [qualifiedName], as it is written in [file], can point to.
   * <p>
   * The method reads the import statements of the file and resolves nothing, so it stays inside the stubs of the file.
   * It sees a top-level import only, which includes an import under `if TYPE_CHECKING:`.
   * <p>
   * An import gives the module of a name, but the file cannot tell where that module takes the name from.
   * A decorator of the same top-level package matches first.
   * If the package holds no such decorator, the module can re-export one, as `propcache._helpers_py` re-exports
   * `functools.cached_property`, so the short name matches.
   * A relative import comes from the package of the file, which holds no known decorator, so the name is not one.
   * A name that the file itself defines belongs to the module of the file, as `classmethod` does in `builtins.pyi`.
   * For any other name the short name is all the file gives. This covers a builtin, a name from a star import,
   * an import inside a function, and a member of a local object as in `@my_property.expression`.
   */
  private static List<PyKnownDecorator> asKnownDecoratorsInFile(QualifiedName qualifiedName, PyFile file) {
    return computeKnownDecoratorsInFile(qualifiedName, file);
  }

  private static List<PyKnownDecorator> computeKnownDecoratorsInFile(QualifiedName qualifiedName, PyFile file) {
    String firstName = qualifiedName.getFirstComponent();
    if (firstName == null) return Collections.emptyList();
    QualifiedName tail = qualifiedName.removeHead(1);
    // A star import keeps the name and gives no imported name, so the short name still finds the decorator further down.
    List<QualifiedName> importedNames = importedQualifiedNames(qualifiedName, file);
    if (importedNames == null) return Collections.emptyList();

    if (!importedNames.isEmpty()) {
      List<PyKnownDecorator> result = new ArrayList<>();
      for (QualifiedName importedName : importedNames) {
        PyKnownDecorator byQualifiedName = findByQualifiedName(importedName);
        if (byQualifiedName != null) {
          result.add(byQualifiedName);
          continue;
        }
        List<PyKnownDecorator> byShortName = findByShortName(Objects.requireNonNull(importedName.getLastComponent()));
        List<PyKnownDecorator> samePackage = new ArrayList<>();
        for (PyKnownDecorator it : byShortName) {
          if (Objects.equals(it.getQualifiedName().getFirstComponent(), importedName.getFirstComponent())) {
            samePackage.add(it);
          }
        }
        result.addAll(samePackage.isEmpty() ? byShortName : samePackage);
      }
      return result;
    }
    PsiElement defined = definedName(file, firstName);
    if (defined != null) {
      String definedQName = defined instanceof PyQualifiedNameOwner qualifiedNameOwner ? qualifiedNameOwner.getQualifiedName() : null;
      if (definedQName == null) return Collections.emptyList();
      String name = Objects.requireNonNull(PyNames.FQN.unqualifyBuiltinName(definedQName));
      PyKnownDecorator known = findByQualifiedName(QualifiedName.fromDottedString(name).append(tail));
      return known != null ? List.of(known) : List.of();
    }
    return findByShortName(Objects.requireNonNull(qualifiedName.getLastComponent()));
  }

  /**
   * Returns the absolute names that the top-level imports of [file] give to [qualifiedName], as it is written in [file].
   * Returns `null` when a relative import binds the first component of [qualifiedName].
   * The method reads the import statements of the file and resolves nothing, so it stays inside the stubs of the file.
   */
  public static @Nullable List<QualifiedName> importedQualifiedNames(QualifiedName qualifiedName, PyFile file) {
    String firstName = qualifiedName.getFirstComponent();
    if (firstName == null) return Collections.emptyList();
    QualifiedName tail = qualifiedName.removeHead(1);
    List<QualifiedName> importedNames = new ArrayList<>();

    for (PyImportElement importElement : file.getImportTargets()) {
      QualifiedName importedQName = importElement.getImportedQName();
      if (importedQName == null) continue;
      String asName = importElement.getAsName();
      if (asName != null) {
        if (asName.equals(firstName)) importedNames.add(importedQName.append(tail));
      }
      else if (Objects.equals(importedQName.getFirstComponent(), firstName)) {
        // "import a.b" binds "a", so the name is already absolute.
        importedNames.add(qualifiedName);
      }
    }
    for (PyFromImportStatement fromImport : file.getFromImports()) {
      if (fromImport.isStarImport()) continue;
      boolean bindsName = false;
      for (PyImportElement it : fromImport.getImportElements()) {
        if (firstName.equals(it.getVisibleName())) {
          bindsName = true;
          break;
        }
      }
      if (!bindsName) continue;
      if (fromImport.getRelativeLevel() != 0) return null;
      QualifiedName source = fromImport.getImportSourceQName();
      if (source == null) continue;
      for (PyImportElement importElement : fromImport.getImportElements()) {
        if (firstName.equals(importElement.getVisibleName())) {
          QualifiedName importedQName = importElement.getImportedQName();
          if (importedQName != null) {
            importedNames.add(source.append(importedQName).append(tail));
          }
        }
      }
    }
    return importedNames;
  }

  private static @Nullable PsiElement definedName(PyFile file, String name) {
    PsiElement result = file.findTopLevelClass(name);
    if (result == null) result = file.findTopLevelFunction(name);
    if (result == null) result = file.findTopLevelAttribute(name);
    return result;
  }

  public static List<PyKnownDecorator> asKnownDecorators(QualifiedName qualifiedName) {
    // The method might have been called during building of PSI stub indexes. Thus, we can't leave this file's boundaries.
    // TODO Use proper local resolve to imported names here
    String lastComponent = qualifiedName.getLastComponent();
    if (lastComponent == null) {
      return Collections.emptyList();
    }
    return findByShortName(lastComponent);
  }

  /**
   * Check that given element has any non-standard (read "unreliable") decorators.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   * @see PyKnownDecorator
   */
  public static boolean hasUnknownDecorator(PyDecoratable element, TypeEvalContext context) {
    return !knownDecoratorsOf(element, context).allKnown();
  }

  /**
   * Checks that given function has any decorators from `abc` module.
   *
   * @param element Python function to check
   * @param context type evaluation context. If it doesn't allow switch to AST, the imports of the file give the qualified name of a decorator.
   * @see PyKnownDecorator
   */
  public static boolean hasAbstractDecorator(PyDecoratable element, TypeEvalContext context) {
    return knownDecoratorsOf(element, context).hasTrait(PyKnownDecorator::isAbstract);
  }

  public static boolean hasGeneratorBasedCoroutineDecorator(PyFunction function, TypeEvalContext context) {
    return knownDecoratorsOf(function, context).hasTrait(PyKnownDecorator::isGeneratorBasedCoroutine);
  }

  public static boolean isResolvedToGeneratorBasedCoroutine(PyCallExpression receiver,
                                                            PyResolveContext resolveContext,
                                                            TypeEvalContext typeEvalContext) {
    return StreamEx
      .of(receiver.multiResolveCalleeFunction(resolveContext))
      .select(PyFunction.class)
      .anyMatch(function -> hasGeneratorBasedCoroutineDecorator(function, typeEvalContext));
  }

  public static boolean hasRedeclarationDecorator(PyFunction function, TypeEvalContext context) {
    return knownDecoratorsOf(function, context).contains(PyKnownDecorator.TYPING_OVERLOAD);
  }

  public static @Nullable PyDecorator findOverrideDecorator(PyDecoratable decoratable, TypeEvalContext context) {
    PyDecoratorList decoratorList = decoratable.getDecoratorList();
    if (decoratorList == null) return null;
    for (PyDecorator decorator : decoratorList.getDecorators()) {
      if (knownDecoratorsOf(decorator, context)
        .containsAny(PyKnownDecorator.TYPING_OVERRIDE, PyKnownDecorator.TYPING_EXTENSIONS_OVERRIDE)) {
        return decorator;
      }
    }
    return null;
  }

  public static boolean hasOverrideDecorator(PyDecoratable decoratable, TypeEvalContext context) {
    return findOverrideDecorator(decoratable, context) != null;
  }

  public static boolean hasUnknownOrChangingSignatureDecorator(PyDecoratable decoratable, TypeEvalContext context) {
    PyElementKnownDecorators decorators = knownDecoratorsOf(decoratable, context);
    return !decorators.allKnown() || decorators.contains(PyKnownDecorator.UNITTEST_MOCK_PATCH);
  }

  public static boolean hasUnknownOrUpdatingAttributesDecorator(PyDecoratable decoratable, TypeEvalContext context) {
    PyElementKnownDecorators decorators = knownDecoratorsOf(decoratable, context);
    if (!decorators.allKnown()) return true;
    // lru_cache adds cache_clear and cache_info, singledispatch adds register
    return decorators.containsAny(PyKnownDecorator.FUNCTOOLS_LRU_CACHE, PyKnownDecorator.FUNCTOOLS_SINGLEDISPATCH);
  }

  @SuppressWarnings("deprecation")
  private static List<PyKnownDecorator> findByShortName(String shortName) {
    return PyKnownDecoratorProvider.EP_NAME.getExtensionList().stream()
      .flatMap(knownDecoratorProvider -> {
        Collection<PyKnownDecorator> decorators = knownDecoratorProvider.getKnownDecorators();
        if (!decorators.isEmpty()) {
          return decorators.stream();
        }
        // Fallback to the old implementation that will be removed in the future release
        String knownDecorator = knownDecoratorProvider.toKnownDecorator(shortName);
        if (knownDecorator != null && !knownDecorator.isEmpty() && !knownDecorator.equals(shortName)) {
          return StreamEx.of(findByShortName(knownDecorator));
        }
        return StreamEx.<PyKnownDecorator>empty();
      })
      .filter(knownDecorator -> knownDecorator.getShortName().equals(shortName))
      .toList();
  }

  private static @Nullable PyKnownDecorator findByQualifiedName(QualifiedName qualifiedName) {
    return PyKnownDecoratorProvider.EP_NAME.getExtensionList().stream()
      .flatMap(knownDecoratorProvider -> knownDecoratorProvider.getKnownDecorators().stream())
      .filter(knownDecorator -> knownDecorator.getQualifiedName().equals(qualifiedName))
      .findFirst()
      .orElse(null);
  }

  public enum FunctoolsWrapsParameters implements FunctionParameter {
    WRAPPED(0, "wrapped");

    private final int myPosition;
    private final String myName;

    FunctoolsWrapsParameters(int position, String name) {
      myPosition = position;
      myName = name;
    }

    @Override
    public int getPosition() {
      return myPosition;
    }

    @Override
    public @Nullable String getName() {
      return myName;
    }
  }
}
