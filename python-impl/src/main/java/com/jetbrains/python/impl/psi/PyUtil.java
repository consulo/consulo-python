// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.google.common.collect.Maps;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.application.util.CachedValuesManager;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.completion.OverwriteEqualsInsertHandler;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementInfo;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementKind;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.stubs.PySetuptoolsNamespaceIndex;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.pyi.PyiStubSuppressor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyCustomPackageIdentifier;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.ParameterizedCachedValueProvider;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.impl.ast.ASTFactory;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.io.NioFiles;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.STATICMETHOD;
import static consulo.language.psi.util.PsiTreeUtil.getStubOrPsiParent;

/**
 * Assorted utility methods for Python code insight.
 * <p>
 * These methods don't depend on the Python runtime.
 *
 * @see PyPsiUtils for utilities used in Python PSI API
 * @see PyUiUtil for UI-related utilities for Python (available in PythonCore plugin)
 */
public final class PyUtil {

  private static final boolean VERBOSE_MODE = System.getenv("_PYCHARM_VERBOSE_MODE") != null;

  private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);

  private PyUtil() {
  }

  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: {@code flattenedParensAndTuples(some_tuple.getExpressions())}.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  public static List<PyExpression> flattenedParensAndTuples(PyExpression... targets) {
    //noinspection unchecked,rawtypes
    return (List)PyUtilCore.flattenedParensAndTuples(targets);
  }

  public static List<PyExpression> flattenedParensAndLists(PyExpression... targets) {
    //noinspection unchecked
    return (List)PyUtilCore.flattenedParensAndLists(targets);
  }

  public static List<PyExpression> flattenedParensAndStars(PyExpression... targets) {
    //noinspection unchecked,rawtypes
    return (List)PyUtilCore.flattenedParensAndStars(targets);
  }

  /**
   * Produce a reasonable representation of a PSI element, good for debugging.
   *
   * @param elt      element to represent; nulls and invalid nodes are ok.
   * @param cutAtEOL if true, representation stops at nearest EOL inside the element.
   * @return the representation.
   */
  public static String getReadableRepr(PsiElement elt, final boolean cutAtEOL) {
    if (elt == null) return "null!";
    ASTNode node = elt.getNode();
    if (node == null) {
      return "null";
    }
    else {
      String s = node.getText();
      int cut_pos;
      if (cutAtEOL) {
        cut_pos = s.indexOf('\n');
      }
      else {
        cut_pos = -1;
      }
      if (cut_pos < 0) cut_pos = s.length();
      return s.substring(0, Math.min(cut_pos, s.length()));
    }
  }

  public static @Nullable PyClass getContainingClassOrSelf(final PsiElement element) {
    PsiElement current = element;
    while (current != null && !(current instanceof PyClass)) {
      current = current.getParent();
    }
    return (PyClass)current;
  }

  /**
   * @param element for which to obtain the file
   * @return PyFile, or null, if there's no containing file, or it is not a PyFile.
   */
  public static @Nullable PyFile getContainingPyFile(PyElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile instanceof PyFile ? (PyFile)containingFile : null;
  }

  /**
   * Returns a quoted string representation, or "null".
   */
  public static String nvl(Object s) {
    if (s != null) {
      return "'" + s.toString() + "'";
    }
    else {
      return "null";
    }
  }

  /**
   * Adds an item into a comma-separated list in a PSI tree. E.g. can turn "foo, bar" into "foo, bar, baz", adding commas as needed.
   *
   * @param parent     the element to represent the list; we're adding a child to it.
   * @param newItem    the element we're inserting (the "baz" in the example).
   * @param beforeThis node to mark the insertion point inside the list; must belong to a child of target. Set to null to add first element.
   * @param isFirst    true if we don't need a comma before the element we're adding.
   * @param isLast     true if we don't need a comma after the element we're adding.
   */
  public static void addListNode(PsiElement parent, PsiElement newItem, ASTNode beforeThis,
                                 boolean isFirst, boolean isLast, boolean addWhitespace) {
    ASTNode node = parent.getNode();
    assert node != null;
    ASTNode itemNode = newItem.getNode();
    assert itemNode != null;
    Project project = parent.getProject();
    PyElementGenerator gen = PyElementGenerator.getInstance(project);
    if (!isFirst) node.addChild(gen.createComma(), beforeThis);
    node.addChild(itemNode, beforeThis);
    if (!isLast) node.addChild(gen.createComma(), beforeThis);
    if (addWhitespace) node.addChild(ASTFactory.whitespace(" "), beforeThis);
  }

  // TODO: move to a more proper place?

  /**
   * Makes sure that 'thing' is not null; else throws an {@link IncorrectOperationException}.
   *
   * @param thing what we check.
   * @return thing, if not null.
   */
  public static <T> T sure(T thing) {
    if (thing == null) throw new IncorrectOperationException();
    return thing;
  }

  /**
   * Makes sure that the 'thing' is true; else throws an {@link IncorrectOperationException}.
   *
   * @param thing what we check.
   */
  public static void sure(boolean thing) {
    if (!thing) throw new IncorrectOperationException();
  }

  public static boolean isAttribute(PyTargetExpression ex) {
    return isInstanceAttribute(ex) || isClassAttribute(ex);
  }

  public static boolean isInstanceAttribute(PyExpression target) {
    if (!(target instanceof PyTargetExpression)) {
      return false;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(target);
    if (owner instanceof PyFunction method) {
      if (method.getContainingClass() != null) {
        if (method.getStub() != null) {
          return true;
        }
        final PyParameter[] params = method.getParameterList().getParameters();
        if (params.length > 0) {
          final PyTargetExpression targetExpr = (PyTargetExpression)target;
          final PyExpression qualifier = targetExpr.getQualifier();
          return qualifier != null && qualifier.getText().equals(params[0].getName());
        }
      }
    }
    return false;
  }

  public static boolean isClassAttribute(PsiElement element) {
    return element instanceof PyTargetExpression && ScopeUtil.getScopeOwner(element) instanceof PyClass;
  }

  public static boolean hasIfNameEqualsMain(PyFile file) {
    final PyIfStatement dunderMain = SyntaxTraverser.psiApi()
      .children(file)
      .filterMap(psi -> psi instanceof PyIfStatement ? ((PyIfStatement)psi) : null)
      .find(ifStatement -> isIfNameEqualsMain(ifStatement));
    return dunderMain != null;
  }

  public static boolean isIfNameEqualsMain(PyIfStatement ifStatement) {
    final PyExpression condition = ifStatement.getIfPart().getCondition();
    return isNameEqualsMain(condition);
  }

  private static boolean isNameEqualsMain(PyExpression condition) {
    if (condition instanceof PyParenthesizedExpression) {
      return isNameEqualsMain(((PyParenthesizedExpression)condition).getContainedExpression());
    }
    if (condition instanceof PyBinaryExpression binaryExpression) {
      if (binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
        return isNameEqualsMain(binaryExpression.getLeftExpression()) || isNameEqualsMain(binaryExpression.getRightExpression());
      }
      if (binaryExpression.getRightExpression() instanceof PyStringLiteralExpression rhs) {
        return binaryExpression.getOperator() == PyTokenTypes.EQEQ &&
               binaryExpression.getLeftExpression().getText().equals(PyNames.NAME) &&
               rhs.getStringValue().equals("__main__");
      }
    }
    return false;
  }

  /**
   * Searches for a method wrapping given element.
   *
   * @param start element presumably inside a method
   * @param deep  if true, allow 'start' to be inside functions nested in a method; else, 'start' must be directly inside a method.
   * @return if not 'deep', [0] is the method and [1] is the class; if 'deep', first several elements may be the nested functions,
   * the last but one is the method, and the last is the class.
   */
  public static @Nullable List<PsiElement> searchForWrappingMethod(PsiElement start, boolean deep) {
    PsiElement seeker = start;
    List<PsiElement> ret = new ArrayList<>(2);
    while (seeker != null) {
      PyFunction func = PsiTreeUtil.getParentOfType(seeker, PyFunction.class, true, PyClass.class);
      if (func != null) {
        PyClass cls = func.getContainingClass();
        if (cls != null) {
          ret.add(func);
          ret.add(cls);
          return ret;
        }
        else if (deep) {
          ret.add(func);
          seeker = func;
        }
        else {
          return null; // no immediate class
        }
      }
      else {
        return null; // no function
      }
    }
    return null;
  }

  public static boolean inSameFile(PsiElement e1, PsiElement e2) {
    final PsiFile f1 = e1.getContainingFile();
    final PsiFile f2 = e2.getContainingFile();
    if (f1 == null || f2 == null) {
      return false;
    }
    return f1 == f2;
  }

  public static boolean isTopLevel(PsiElement element) {
    return PyUtilCore.isTopLevel(element);
  }

  public static void deletePycFiles(String pyFilePath) {
    try {
      if (pyFilePath.endsWith(PyNames.DOT_PY)) {
        Files.deleteIfExists(Path.of(pyFilePath + "c"));
        Files.deleteIfExists(Path.of(pyFilePath + "o"));
        var file = Path.of(pyFilePath);
        var pycache = file.resolveSibling(PyNames.PYCACHE);
        if (Files.isDirectory(pycache)) {
          var shortName = FileUtil.getNameWithoutExtension(NioFiles.getFileName(file));
          for (var cacheFile : NioFiles.list(pycache)) {
            var cacheFileName = NioFiles.getFileName(cacheFile);
            if (FileUtil.extensionEquals(cacheFileName, "pyc")) {
              var nameWithMagic = FileUtil.getNameWithoutExtension(cacheFileName);
              if (FileUtil.getNameWithoutExtension(nameWithMagic).equals(shortName)) {
                Files.deleteIfExists(cacheFile);
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String getElementNameWithoutExtension(PsiNamedElement psiNamedElement) {
    return psiNamedElement instanceof PyFile
           ? FileUtil.getNameWithoutExtension(((PyFile)psiNamedElement).getName())
           : psiNamedElement.getName();
  }

  public static boolean hasUnresolvedAncestors(PyClass cls, TypeEvalContext context) {
    for (PyClassLikeType type : cls.getAncestorTypes(context)) {
      if (type == null) {
        return true;
      }
    }
    return false;
  }

  public static AccessDirection getPropertyAccessDirection(PyFunction function) {
    final Property property = function.getProperty();
    if (property != null) {
      if (property.getGetter().valueOrNull() == function) {
        return AccessDirection.READ;
      }
      if (property.getSetter().valueOrNull() == function) {
        return AccessDirection.WRITE;
      }
      else if (property.getDeleter().valueOrNull() == function) {
        return AccessDirection.DELETE;
      }
    }
    return AccessDirection.READ;
  }

  public static void removeQualifier(final PyReferenceExpression element) {
    final PyExpression qualifier = element.getQualifier();
    if (qualifier == null) return;

    if (qualifier instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)qualifier).getCallee();
      if (callee instanceof PyReferenceExpression) {
        final PyExpression calleeQualifier = ((PyReferenceExpression)callee).getQualifier();
        if (calleeQualifier != null) {
          qualifier.replace(calleeQualifier);
          return;
        }
      }
    }
    final PsiElement dot = PyPsiUtils.getNextNonWhitespaceSibling(qualifier);
    if (dot != null) dot.delete();
    qualifier.delete();
  }

  public static boolean isOwnScopeComprehension(PyComprehensionElement comprehension) {
    final boolean isAtLeast30 = !LanguageLevel.forElement(comprehension).isPython2();
    final boolean isListComprehension = comprehension instanceof PyListCompExpression;
    return !isListComprehension || isAtLeast30;
  }

  public static ASTNode createNewName(PyElement element, String name) {
    return PyElementGenerator.getInstance(element.getProject()).createNameIdentifier(name, LanguageLevel.forElement(element));
  }

  /**
   * Finds element declaration by resolving its references to the top but not further than file (to prevent un-stubbing)
   *
   * @param elementToResolve element to resolve
   * @return its declaration
   */
  public static PsiElement resolveToTheTop(final PsiElement elementToResolve) {
    PsiElement currentElement = elementToResolve;
    final Set<PsiElement> checkedElements = new HashSet<>(); // To prevent PY-20553
    while (true) {
      final PsiReference reference = currentElement.getReference();
      if (reference == null) {
        break;
      }
      final PsiElement resolve = reference.resolve();
      if (resolve == null || checkedElements.contains(resolve) || resolve.equals(currentElement) || !inSameFile(resolve, currentElement)) {
        break;
      }
      currentElement = resolve;
      checkedElements.add(resolve);
    }
    return currentElement;
  }

  // Note that returned list may contain null items, e.g. for unresolved import elements, originally wrapped
  //  in `com.jetbrains.python.psi.resolve.ImportedResolveResult`
  //  TODO: it would be a good idea to revise `filterTopPriority` to return the import definer when the element is null
  public static List<@Nullable PsiElement> multiResolveTopPriority(PsiElement element,
                                                                            PyResolveContext resolveContext) {
    if (element instanceof PyReferenceOwner referenceOwner) {
      return multiResolveTopPriority(referenceOwner.getReference(resolveContext));
    }
    else {
      final PsiReference reference = element.getReference();
      return reference != null ? Collections.singletonList(reference.resolve()) : Collections.emptyList();
    }
  }

  public static List<PsiElement> multiResolveTopPriority(PsiPolyVariantReference reference) {
    return filterTopPriorityElements(Arrays.asList(reference.multiResolve(false)));
  }

  /**
   * @deprecated Use {@link #filterTopPriorityElements(List)}
   */
  @Deprecated(forRemoval = true)
  public static List<PsiElement> filterTopPriorityResults(ResolveResult [] resolveResults) {
    if (resolveResults.length == 0) return Collections.emptyList();

    final int maxRate = getMaxRate(Arrays.asList(resolveResults));
    return StreamEx
      .of(resolveResults)
      .filter(resolveResult -> getRate(resolveResult) >= maxRate)
      .map(ResolveResult::getElement)
      .nonNull()
      .toList();
  }

  public static <E extends ResolveResult> List<E> filterTopPriorityResults(List<? extends E> resolveResults) {
    if (resolveResults.isEmpty()) return Collections.emptyList();

    final int maxRate = getMaxRate(resolveResults);
    return ContainerUtil.filter(resolveResults, resolveResult -> getRate(resolveResult) >= maxRate);
  }

  public static <E extends ResolveResult> List<PsiElement> filterTopPriorityElements(List<? extends E> resolveResults) {
    return ContainerUtil.mapNotNull(filterTopPriorityResults(resolveResults), ResolveResult::getElement);
  }

  private static int getMaxRate(List<? extends ResolveResult> resolveResults) {
    return resolveResults
      .stream()
      .mapToInt(PyUtil::getRate)
      .max()
      .orElse(Integer.MIN_VALUE);
  }

  private static int getRate(ResolveResult resolveResult) {
    return resolveResult instanceof RatedResolveResult ? ((RatedResolveResult)resolveResult).getRate() : RatedResolveResult.RATE_NORMAL;
  }

  /**
   * Gets class init method
   *
   * @param pyClass class where to find init
   * @return class init method if any
   */
  public static @Nullable PyFunction getInitMethod(final PyClass pyClass) {
    return pyClass.findMethodByName(PyNames.INIT, false, null);
  }

  /**
   * Clone of C# "as" operator.
   * Checks if expression has correct type and casts it if it has. Returns null otherwise.
   * It saves coder from "instanceof / cast" chains.
   *
   * @param expression expression to check
   * @param clazz      class to cast
   * @param <T>        class to cast
   * @return expression casted to appropriate type (if could be casted). Null otherwise.
   */
  public static <T> @Nullable T as(@Nullable final Object expression, final Class<T> clazz) {
    //noinspection unchecked
    return ObjectUtil.tryCast(
      expression,
      (Class<T>)switch (clazz) {
        case Class<?> c when c == boolean.class -> Boolean.class;
        case Class<?> c when c == int.class -> Integer.class;
        default -> clazz;
      });
  }

  // TODO: Move to PsiElement?

  /**
   * Searches for references injected to element with certain type
   *
   * @param element       element to search injected references for
   * @param expectedClass expected type of element reference resolved to
   * @param <T>           expected type of element reference resolved to
   * @return resolved element if found or null if not found
   */
  public static <T extends PsiElement> @Nullable T findReference(final PsiElement element, final Class<T> expectedClass) {
    for (final PsiReference reference : element.getReferences()) {
      final T result = as(reference.resolve(), expectedClass);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Converts collection to list of certain type
   *
   * @param expression   expression of collection type
   * @param elementClass expected element type
   * @param <T>          expected element type
   * @return list of elements of expected element type
   */
  public static <T> List<T> asList(@Nullable final Collection<?> expression, final Class<? extends T> elementClass) {
    if ((expression == null) || expression.isEmpty()) {
      return Collections.emptyList();
    }
    final List<T> result = new ArrayList<>();
    for (final Object element : expression) {
      final T toAdd = as(element, elementClass);
      if (toAdd != null) {
        result.add(toAdd);
      }
    }
    return result;
  }

  /**
   * Calculates and caches value based on param. Think about it as about map with param as key which flushes on each psi modification.
   * <p>
   * For nullable function see {@link #getNullableParameterizedCachedValue(PsiElement, Object, Function)}.
   * <p>
   * This function is used instead of {@link CachedValuesManager#createParameterizedCachedValue(ParameterizedCachedValueProvider, boolean)}
   * because parameter is not used as key there but used only for first calculation. Hence this should have functional dependency on element.
   *
   * @param element place to store cache
   * @param param   param to be used as key
   * @param f       function to produce value for key
   * @param <T>     value type
   * @param <P>     key type
   */
  public static <T, P> T getParameterizedCachedValue(PsiElement element,
                                                              @Nullable P param,
                                                              Function<P, T> f) {
    final T result = getNullableParameterizedCachedValue(element, param, f);
    assert result != null;
    return result;
  }

  /**
   * Same as {@link #getParameterizedCachedValue(PsiElement, Object, Function)} but allows nulls.
   */
  public static <T, P> @Nullable T getNullableParameterizedCachedValue(PsiElement element,
                                                                       @Nullable P param,
                                                                       Function<P, @Nullable T> f) {
    final CachedValuesManager manager = CachedValuesManager.getManager(element.getProject());
    final Map<Optional<P>, Optional<T>> cache = LanguageCachedValueUtil.getCachedValue(element, manager.getKeyForClass(f.getClass()), () -> {
      // concurrent hash map is a null-hostile collection
      return CachedValueProvider.Result.create(Maps.newConcurrentMap(), PsiModificationTracker.MODIFICATION_COUNT);
    });
    // Don't use ConcurrentHashMap#computeIfAbsent(), it blocks if the function tries to update the cache recursively for the same key
    // during computation. We can accept here that some values will be computed several times due to non-atomic updates.
    final Optional<P> wrappedParam = Optional.ofNullable(param);
    Optional<T> value = cache.get(wrappedParam);
    if (value == null) {
      value = Optional.ofNullable(f.apply(param));
      cache.put(wrappedParam, value);
    }
    return value.orElse(null);
  }

  /**
   * This method is allowed to be called from any thread, but in general you should not set {@code modal=true} if you're calling it
   * from the write action, because in this case {@code function} will be executed right in the current thread (presumably EDT)
   * without any progress whatsoever to avoid possible deadlock.
   *
   * @see com.intellij.openapi.application.impl.ApplicationImpl#runProcessWithProgressSynchronously(Runnable, String, boolean, boolean, Project, JComponent, String)
   */
  public static void runWithProgress(@Nullable Project project, String title,
                                     boolean modal, boolean canBeCancelled, final Consumer<? super ProgressIndicator> function) {
    // TODO consulo: Task.Backgroundable.toModalIfNeeded missing
    if (modal) {
      ProgressManager.getInstance().run(new Task.Modal(project, title, canBeCancelled) {
        @Override
        public void run(ProgressIndicator indicator) {
          function.accept(indicator);
        }
      });
    }
    else {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, title, canBeCancelled) {
        @Override
        public void run(ProgressIndicator indicator) {
          function.accept(indicator);
        }
      });
    }
  }

  /**
   * Executes code only if <pre>_PYCHARM_VERBOSE_MODE</pre> is set in env (which should be done for debug purposes only)
   *
   * @param runnable code to call
   */
  public static void verboseOnly(final Runnable runnable) {
    if (VERBOSE_MODE) {
      runnable.run();
    }
  }

  public static boolean isPy2ReservedWord(PyReferenceExpression node) {
    if (LanguageLevel.forElement(node).isPython2()) {
      if (!node.isQualified()) {
        final String name = node.getName();
        if (PyNames.NONE.equals(name) || PyNames.FALSE.equals(name) || PyNames.TRUE.equals(name)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Retrieves the document from {@link PsiDocumentManager} using the anchor PSI element and, if it's not null,
   * passes it to the consumer function.
   * <p>
   * The document is first released from pending PSI operations and then committed after the function has been applied
   * in a {@code try/finally} block, so that subsequent operations on PSI could be performed.
   *
   * @see PsiDocumentManager#doPostponedOperationsAndUnblockDocument(Document)
   * @see PsiDocumentManager#commitDocument(Document)
   * @see #updateDocumentUnblockedAndCommitted(PsiElement, Function)
   */
  public static void updateDocumentUnblockedAndCommitted(PsiElement anchor, Consumer<? super Document> consumer) {
    PyUtilCore.updateDocumentUnblockedAndCommitted(anchor, consumer);
  }

  public static <T> @Nullable T updateDocumentUnblockedAndCommitted(PsiElement anchor,
                                                                    Function<? super Document, ? extends T> func) {
    return PyUtilCore.updateDocumentUnblockedAndCommitted(anchor, func);
  }

  public static @Nullable PyType getReturnTypeToAnalyzeAsCallType(PyFunction function, TypeEvalContext context) {
    if (isInitMethod(function)) {
      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        PyType providedClassType = PyTypeChecker.findGenericDefinitionType(cls, context);
        if (providedClassType != null) return providedClassType;

        final PyInstantiableType classType = as(context.getType(cls), PyInstantiableType.class);
        if (classType != null) {
          return classType.toInstance();
        }
      }
    }

    return context.getReturnType(function);
  }

  private static final Key<Boolean> ADDED_FRAGMENT_PARENTHESES = Key.create("PyUtil.ADDED_FRAGMENT_PARENTHESES");

  /**
   * Create a new expressions fragment from the given text, setting the specified element as its context,
   * and return the contained expression of the first expression statement in it.
   * <p>
   * The text can span several lines, e.g. a parameter annotation stored in a stub.
   * In the source, the enclosing brackets make such line breaks legal.
   * If the text contains a line break and does not parse as one expression statement,
   * it is parsed again inside parentheses, and the expression inside them is returned.
   * {@link #getFragmentContextAwareParent} skips these parentheses.
   *
   * @param expressionText text of the expression
   * @param context        context element used to resolve symbols in the expression
   * @return instance of {@link PyExpression} as described
   * @see PyExpressionCodeFragment
   */
  public static @Nullable PyExpression createExpressionFromFragment(String expressionText, PsiElement context) {
    var statement = parseExpressionStatement(expressionText, context);
    if (expressionText.indexOf('\n') >= 0 && (statement == null || PsiTreeUtil.skipWhitespacesAndCommentsForward(statement) != null) &&
        parseExpressionStatement("(" + expressionText + "\n)", context) instanceof PyExpressionStatement wrapped &&
        wrapped.getExpression() instanceof PyParenthesizedExpression parenthesized &&
        parenthesized.getContainedExpression() instanceof PyExpression contained) {
      parenthesized.putUserData(ADDED_FRAGMENT_PARENTHESES, Boolean.TRUE);
      return contained;
    }
    return statement != null ? statement.getExpression() : null;
  }

  private static @Nullable PyExpressionStatement parseExpressionStatement(String text, PsiElement context) {
    var codeFragment = new PyExpressionCodeFragmentImpl(context.getProject(), "dummy.py", text, false);
    codeFragment.setContext(context);
    return codeFragment.getFirstChild() instanceof PyExpressionStatement statement ? statement : null;
  }

  /**
   * Returns the contextual PSI element if the given element is inside a {@link PyExpressionCodeFragment},
   * created by {@link PyUtil#createExpressionFromFragment}, otherwise the element itself is returned.
   */
  public static @Nullable PsiElement getFragmentContext(@Nullable PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PyExpressionCodeFragment.class) == null
           ? element
           : element.getContainingFile().getContext();
  }

  /**
   * Returns the parent of the given element.
   * Supports parent boundary transition if the given element is inside a {@link PyExpressionCodeFragment}
   * created by {@link PyUtil#createExpressionFromFragment}.
   * The parentheses that this method adds around a text with a line break are skipped too.
   */
  public static @Nullable PsiElement getFragmentContextAwareParent(@Nullable PsiElement element) {
    if (element == null) return null;
    var parent = getStubOrPsiParent(element);
    if (parent == null) return null;
    if (parent instanceof PyParenthesizedExpression && parent.getUserData(ADDED_FRAGMENT_PARENTHESES) != null) {
      parent = getStubOrPsiParent(parent);
      if (parent == null) return null;
    }
    if (parent instanceof PyExpressionStatement && getStubOrPsiParent(parent) instanceof PyExpressionCodeFragment fragment) {
      return fragment.getContext();
    }
    return parent;
  }

  public static boolean isRoot(PsiFileSystemItem directory) {
    if (directory == null) return true;
    VirtualFile vFile = directory.getVirtualFile();
    if (vFile == null) return true;
    Project project = directory.getProject();
    return isRoot(vFile, project);
  }

  public static boolean isRoot(VirtualFile directory, Project project) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    return Comparing.equal(fileIndex.getClassRootForFile(directory), directory) ||
           Comparing.equal(fileIndex.getContentRootForFile(directory), directory) ||
           Comparing.equal(fileIndex.getSourceRootForFile(directory), directory);
  }

  /**
   * Checks whether {@param file} representing Python module or package can be imported into {@param file}.
   */
  public static boolean isImportable(PsiFile targetFile, PsiFileSystemItem file) {
    PsiDirectory parent = (PsiDirectory)file.getParent();
    return parent != null && file != targetFile &&
           (isRoot(parent) ||
            parent == targetFile.getParent() ||
            isPackage(parent, false, null));
  }

  public static Collection<String> collectUsedNames(@Nullable final PsiElement scope) {
    if (!(scope instanceof PyClass) && !(scope instanceof PyFile) && !(scope instanceof PyFunction)) {
      return Collections.emptyList();
    }
    final Set<String> variables = new HashSet<>() {
      @Override
      public boolean add(String s) {
        return s != null && super.add(s);
      }
    };
    scope.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(final PyTargetExpression node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyNamedParameter(final PyNamedParameter node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        if (!node.isQualified()) {
          variables.add(node.getReferencedName());
        }
        else {
          super.visitPyReferenceExpression(node);
        }
      }

      @Override
      public void visitPyFunction(final PyFunction node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyClass(final PyClass node) {
        variables.add(node.getName());
      }
    });
    return variables;
  }

  /**
   * If argument is a PsiDirectory, turn it into a PsiFile that points to __init__.py in that directory.
   * If there's no __init__.py there, null is returned, there's no point to resolve to a dir which is not a package.
   * Alas, resolve() and multiResolve() can't return anything but a PyFile or PsiFileImpl.isPsiUpToDate() would fail.
   * This is because isPsiUpToDate() relies on identity of objects returned by FileViewProvider.getPsi().
   * If we ever need to exactly tell a dir from __init__.py, that logic has to change.
   *
   * @param target a resolve candidate.
   * @return a PsiFile if target was a PsiDirectory, or null, or target unchanged.
   */
  public static @Nullable PsiElement turnDirIntoInit(@Nullable PsiElement target) {
    if (target instanceof PsiDirectory dir) {
      final PsiFile initStub = dir.findFile(PyNames.INIT_DOT_PYI);
      if (initStub != null && !PyiStubSuppressor.isIgnoredStub(initStub)) {
        return initStub;
      }
      final PsiFile initFile = dir.findFile(PyNames.INIT_DOT_PY);
      if (initFile != null) {
        return initFile; // ResolveImportUtil will extract directory part as needed, everyone else are better off with a file.
      }
      else {
        return null;
      } // dir without __init__.py does not resolve
    }
    else {
      return target;
    } // don't touch non-dirs
  }

  public static @Nullable PsiElement turnDirIntoInitPy(@Nullable PsiElement target) {
    if (!(target instanceof PsiDirectory psiDirectory)) return target;
    return psiDirectory.findFile(PyNames.INIT_DOT_PY);
  }

  public static @Nullable PsiElement turnDirIntoInitPyi(@Nullable PsiElement target) {
    if (!(target instanceof PsiDirectory psiDirectory)) return target;
    final PsiFile initStub = psiDirectory.findFile(PyNames.INIT_DOT_PYI);
    if (initStub != null && !PyiStubSuppressor.isIgnoredStub(initStub)) {
      return initStub;
    }
    return null;
  }

  /**
   * If directory is a PsiDirectory, that is also a valid Python package, return PsiFile that points to __init__.py,
   * if such file exists, or directory itself (i.e. namespace package). Otherwise, return {@code null}.
   * Unlike {@link #turnDirIntoInit(PsiElement)} this function handles namespace packages and
   * accepts only PsiDirectories as target.
   *
   * @param directory directory to check
   * @param anchor    optional PSI element to determine language level as for {@link #isPackage(PsiDirectory, PsiElement)}
   * @return PsiFile or PsiDirectory, if target is a Python package and {@code null} null otherwise
   */
  public static @Nullable PsiElement getPackageElement(PsiDirectory directory, @Nullable PsiElement anchor) {
    if (isPackage(directory, anchor)) {
      final PsiElement init = turnDirIntoInit(directory);
      if (init != null) {
        return init;
      }
      return directory;
    }
    return null;
  }

  /**
   * If target is a Python module named __init__.py file, return its directory. Otherwise return target unchanged.
   *
   * @param target PSI element to check
   * @return PsiDirectory or target unchanged
   */
  public static @Nullable PsiElement turnInitIntoDir(@Nullable PsiElement target) {
    if (target instanceof PyFile && isPackage((PsiFile)target)) {
      return ((PsiFile)target).getContainingDirectory();
    }
    return target;
  }

  /**
   * @see #isPackage(PsiDirectory, boolean, PsiElement)
   */
  public static boolean isPackage(PsiDirectory directory, @Nullable PsiElement anchor) {
    return isPackage(directory, true, anchor);
  }

  /**
   * Checks that given PsiDirectory can be treated as Python package, i.e. it's either contains __init__.py or it's a namespace package
   * (effectively any directory in Python 3.3 and above). Setuptools namespace packages can be checked as well, but it requires access to
   * {@link PySetuptoolsNamespaceIndex} and may slow things down during update of project indexes.
   * Also note that this method does not check that directory itself and its parents have valid importable names,
   * use {@link PyNames#isIdentifier(String)} for this purpose.
   *
   * @param directory               PSI directory to check
   * @param checkSetupToolsPackages whether setuptools namespace packages should be considered as well
   * @param anchor                  optional anchor element to determine language level
   * @return whether given directory is Python package
   * @see PyNames#isIdentifier(String)
   */
  public static boolean isPackage(PsiDirectory directory, boolean checkSetupToolsPackages, @Nullable PsiElement anchor) {
    if (isExplicitPackage(directory)) return true;
    PsiElement element = anchor != null ? anchor : directory;
    final LanguageLevel level = LanguageLevel.forElement(element);
    if (!level.isPython2()) {
      return true;
    }
    return checkSetupToolsPackages && isSetuptoolsNamespacePackage(directory);
  }

  public static boolean isPackage(PsiFile file) {
    for (PyCustomPackageIdentifier customPackageIdentifier : PyCustomPackageIdentifier.EP_NAME.getExtensions()) {
      if (customPackageIdentifier.isPackageFile(file)) {
        return true;
      }
    }
    return PyNames.INIT_DOT_PY.equals(file.getName());
  }

  public static boolean isPackage(PsiFileSystemItem anchor, @Nullable PsiElement location) {
    return anchor instanceof PsiFile ? isPackage((PsiFile)anchor) :
           anchor instanceof PsiDirectory && isPackage((PsiDirectory)anchor, location);
  }

  public static boolean isCustomPackage(PsiDirectory directory) {
    for (PyCustomPackageIdentifier customPackageIdentifier : PyCustomPackageIdentifier.EP_NAME.getExtensions()) {
      if (customPackageIdentifier.isPackage(directory)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isExplicitPackage(PsiDirectory directory) {
    return isOrdinaryPackage(directory) || isCustomPackage(directory);
  }

  private static boolean isSetuptoolsNamespacePackage(PsiDirectory directory) {
    final String packagePath = getPackagePath(directory);
    return packagePath != null && !PySetuptoolsNamespaceIndex.find(packagePath, directory.getProject()).isEmpty();
  }

  private static @Nullable String getPackagePath(PsiDirectory directory) {
    final QualifiedName name = QualifiedNameFinder.findShortestImportableQName(directory);
    return name != null ? name.toString() : null;
  }

  /**
   * Counts initial underscores of an identifier.
   *
   * @param name identifier
   * @return 0 if null or no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(@Nullable String name) {
    return PyUtilCore.getInitialUnderscores(name);
  }

  /**
   * @return true iff the name looks like a class-private one, starting with two underscores but not ending with two underscores.
   *
   * @deprecated use {@link PyAstElement#getProtectionLevel()} or {@link PyNames#isDunder(String)}
   */
  @Deprecated
  public static boolean isClassPrivateName(String name) {
    return name.startsWith("__") && !name.endsWith("__");
  }

  /**
   * Constructs new lookup element for completion of keyword argument with equals sign appended.
   *
   * @param name           name of the parameter
   * @param settingsAnchor file to check code style settings and surround equals sign with spaces if necessary
   * @return lookup element
   */
  public static LookupElement createNamedParameterLookup(String name,
                                                                  PsiFile settingsAnchor,
                                                                  boolean addEquals) {
    final String suffix;
    if (addEquals) {
      if (PythonCodeStyleService.getInstance().isSpaceAroundEqInKeywordArgument(settingsAnchor)) {
        suffix = " = ";
      }
      else {
        suffix = "=";
      }
    }
    else {
      suffix = "";
    }
    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(name + suffix).withIcon(PlatformIconGroup.nodesParameter());
    lookupElementBuilder = lookupElementBuilder.withInsertHandler(OverwriteEqualsInsertHandler.INSTANCE);
    lookupElementBuilder.putUserData(PyCompletionMlElementInfo.getKey(), PyCompletionMlElementKind.NAMED_ARG.asInfo());
    return PrioritizedLookupElement.withGrouping(lookupElementBuilder, 1);
  }

  public static LookupElement createNamedParameterLookup(String name, PsiFile settingsAnchor) {
    return createNamedParameterLookup(name, settingsAnchor, true);
  }

  /**
   * Peels argument expression of parentheses and of keyword argument wrapper
   *
   * @param expr an item of getArguments() array
   * @return expression actually passed as argument
   */
  public static @Nullable PyExpression peelArgument(PyExpression expr) {
    while (expr instanceof PyParenthesizedExpression) expr = ((PyParenthesizedExpression)expr).getContainedExpression();
    if (expr instanceof PyKeywordArgument) expr = ((PyKeywordArgument)expr).getValueExpression();
    return expr;
  }

  public static String getFirstParameterName(PyFunction container) {
    String selfName = PyNames.CANONICAL_SELF;
    if (container != null) {
      final PyParameter[] params = container.getParameterList().getParameters();
      if (params.length > 0) {
        final PyNamedParameter named = params[0].getAsNamed();
        if (named != null) {
          selfName = named.getName();
        }
      }
    }
    return selfName;
  }

  /**
   * @return Source roots <strong>and</strong> content roots for element's project
   */
  public static Collection<VirtualFile> getSourceRoots(PsiElement foothold) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(foothold);
    if (module != null) {
      return getSourceRoots(module);
    }
    return Collections.emptyList();
  }

  /**
   * @return Source roots <strong>and</strong> content roots for module
   */
  public static Collection<VirtualFile> getSourceRoots(Module module) {
    final Set<VirtualFile> result = new LinkedHashSet<>();
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    Collections.addAll(result, manager.getSourceRoots());
    Collections.addAll(result, manager.getContentRoots());
    return result;
  }

  public static @Nullable VirtualFile findInRoots(Module module, String path) {
    if (module != null) {
      for (VirtualFile root : getSourceRoots(module)) {
        VirtualFile file = root.findFileByRelativePath(path);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }

  public static @Nullable List<String> strListValue(@Nullable PyExpression value) {
    return PyUtilCore.strListValue(value);
  }

  public static Map<String, PyExpression> dictValue(PyDictLiteralExpression dict) {
    Map<String, PyExpression> result = Maps.newLinkedHashMap();
    for (PyKeyValueExpression keyValue : dict.getElements()) {
      PyExpression key = keyValue.getKey();
      PyExpression value = keyValue.getValue();
      if (key instanceof PyStringLiteralExpression) {
        result.put(((PyStringLiteralExpression)key).getStringValue(), value);
      }
    }
    return result;
  }

  /**
   * @param what     thing to search for
   * @param variants things to search among
   * @return true iff what.equals() one of the variants.
   */
  public static <T> boolean among(T what, T... variants) {
    for (T s : variants) {
      if (what.equals(s)) return true;
    }
    return false;
  }

  public static @Nullable String getKeywordArgumentString(PyCallExpression expr, String keyword) {
    return PyPsiUtils.strValue(expr.getKeywordArgument(keyword));
  }

  public static boolean isExceptionClass(PyClass pyClass) {
    if (isBaseException(pyClass.getQualifiedName())) {
      return true;
    }
    for (PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback(pyClass.getProject()))) {
      if (type != null && isBaseException(type.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBaseException(String name) {
    return name != null && (name.contains("BaseException") || name.startsWith("exceptions."));
  }

  public static @Nullable PyType convertToType(PyLiteralKind literalKind, PyBuiltinCache builtinCache) {
    var result = switch (literalKind) {
      case INT -> builtinCache.getIntType();
      case FLOAT -> builtinCache.getFloatType();
      case STRING -> builtinCache.getStrType();
      case BOOL -> builtinCache.getBoolType();
      case NONE -> builtinCache.getNoneType();
    };
    return result != null ? result : PyAnyType.getUnknown();
  }

  public static @Nullable PyClassType selectCallableTypeRuntimeClass(PyCallableType callableType,
                                                                     @Nullable PyExpression location,
                                                                     TypeEvalContext context) {
    String className;
    if (location instanceof PyReferenceExpression re && isBoundMethodReference(callableType, re, context)) {
      className = PyNames.TYPES_METHOD_TYPE;
    }
    else {
      className = PyNames.TYPES_FUNCTION_TYPE;
    }
    PyCallable callable = callableType.getCallable();
    PyClass cls = callable != null ? PyPsiFacade.getInstance(callable.getProject()).createClassByQName(className, callable) : null;
    return cls != null ? new PyClassTypeImpl(cls, false) : null;
  }

  private static boolean isBoundMethodReference(PyCallableType callableType,
                                                PyReferenceExpression location,
                                                TypeEvalContext context) {
    final PyFunction function = as(callableType.getCallable(), PyFunction.class);
    final boolean isNonStaticMethod = function != null && function.getContainingClass() != null && function.getModifier() != STATICMETHOD;
    if (isNonStaticMethod) {
      // In Python 2 unbound methods have __method fake type
      if (LanguageLevel.forElement(location).isPython2()) {
        return true;
      }
      final PyExpression qualifier;
      if (location.isQualified()) {
        qualifier = location.getQualifier();
      }
      else {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        qualifier = ContainerUtil.getLastItem(location.followAssignmentsChain(resolveContext).getQualifiers());
      }
      if (qualifier != null) {
        final PyType qualifierType = context.getType(qualifier);
        if (PyTypeUtil.toStream(qualifierType).select(PyClassType.class).anyMatch(it -> !it.isDefinition())) {
          return true;
        }
      }
    }
    return false;
  }

  public static final class MethodFlags {

    private final boolean myIsStaticMethod;
    private final boolean myIsMetaclassMethod;
    private final boolean myIsSpecialMetaclassMethod;
    private final boolean myIsClassMethod;

    /**
     * @return true iff the method belongs to a metaclass (an ancestor of 'type').
     */
    public boolean isMetaclassMethod() {
      return myIsMetaclassMethod;
    }

    /**
     * @return iff isMetaclassMethod and the method is either __init__ or __call__.
     */
    public boolean isSpecialMetaclassMethod() {
      return myIsSpecialMetaclassMethod;
    }

    public boolean isStaticMethod() {
      return myIsStaticMethod;
    }

    public boolean isClassMethod() {
      return myIsClassMethod;
    }

    private MethodFlags(boolean isClassMethod, boolean isStaticMethod, boolean isMetaclassMethod, boolean isSpecialMetaclassMethod) {
      myIsClassMethod = isClassMethod;
      myIsStaticMethod = isStaticMethod;
      myIsMetaclassMethod = isMetaclassMethod;
      myIsSpecialMetaclassMethod = isSpecialMetaclassMethod;
    }

    /**
     * @param node a function
     * @return a new flags object, or null if the function is not a method
     */
    public static @Nullable MethodFlags of(PyFunction node) {
      PyClass cls = node.getContainingClass();
      if (cls != null) {
        PyFunction.Modifier modifier = node.getModifier();
        boolean isMetaclassMethod = false;
        PyClass type_cls = PyBuiltinCache.getInstance(node).getClass("type");
        for (PyClass ancestor_cls : cls.getAncestorClasses(null)) {
          if (ancestor_cls == type_cls) {
            isMetaclassMethod = true;
            break;
          }
        }
        final String method_name = node.getName();
        boolean isSpecialMetaclassMethod = isMetaclassMethod && method_name != null && among(method_name, PyNames.INIT, "__call__");
        return new MethodFlags(modifier == CLASSMETHOD, modifier == STATICMETHOD, isMetaclassMethod, isSpecialMetaclassMethod);
      }
      return null;
    }

    //TODO: Doc
    public boolean isInstanceMethod() {
      return !(myIsClassMethod || myIsStaticMethod);
    }
  }

  public static boolean isSuperCall(PyCallExpression node) {
    PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
    if (klass == null) return false;
    PyExpression callee = node.getCallee();
    if (callee == null) return false;
    String name = callee.getName();
    if (PyNames.SUPER.equals(name)) {
      PsiReference reference = callee.getReference();
      if (reference == null) return false;
      PsiElement resolved = reference.resolve();
      PyBuiltinCache cache = PyBuiltinCache.getInstance(node);
      if (resolved != null && cache.isBuiltin(resolved)) {
        PyExpression[] args = node.getArguments();
        if (args.length > 0) {
          String firstArg = args[0].getText();
          if (firstArg.equals(klass.getName()) || firstArg.equals(PyNames.CANONICAL_SELF + "." + PyNames.__CLASS__)) {
            return true;
          }
          for (PyClass s : klass.getAncestorClasses(null)) {
            if (firstArg.equals(s.getName())) {
              return true;
            }
          }
        }
        else {
          return true;
        }
      }
    }
    return false;
  }

  public static @Nullable PsiElement findPrevAtOffset(PsiFile psiFile,
                                                      int caretOffset,
                                                      Class<? extends PsiElement> ... toSkip) {
    return PyUtilCore.findPrevAtOffset(psiFile, caretOffset, toSkip);
  }

  public static @Nullable PsiElement findNonWhitespaceAtOffset(PsiFile psiFile, int caretOffset) {
    return PyUtilCore.findNonWhitespaceAtOffset(psiFile, caretOffset);
  }

  public static @Nullable PsiElement findElementAtOffset(PsiFile psiFile, int caretOffset) {
    PsiElement element = findPrevAtOffset(psiFile, caretOffset);
    if (element == null) {
      element = findNextAtOffset(psiFile, caretOffset);
    }
    return element;
  }

  public static @Nullable PsiElement findNextAtOffset(final PsiFile psiFile,
                                                      int caretOffset,
                                                      Class<? extends PsiElement> ... toSkip) {
    return PyUtilCore.findNextAtOffset(psiFile, caretOffset, toSkip);
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__}.
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  public static boolean isInitMethod(@Nullable PsiElement element) {
    final PyFunction function = as(element, PyFunction.class);
    return function != null && PyNames.INIT.equals(function.getName()) && function.getContainingClass() != null;
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  public static boolean isNewMethod(@Nullable PsiElement element) {
    return PyUtilCore.isNewMethod(element);
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__} or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  public static boolean isInitOrNewMethod(@Nullable PsiElement element) {
    return PyUtilCore.isInitOrNewMethod(element);
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__},
   * {@code __init_subclass__}, or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  public static boolean isConstructorLikeMethod(@Nullable PsiElement element) {
    return PyUtilCore.isConstructorLikeMethod(element);
  }

  /**
   * @return containing class for a method named {@code __init__} or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   */
  public static @Nullable PyClass turnConstructorIntoClass(@Nullable PyFunction function) {
    return isInitOrNewMethod(function) ? function.getContainingClass() : null;
  }

  public static boolean isStarImportableFrom(String name, PyFile file) {
    final List<String> dunderAll = file.getDunderAll();
    return dunderAll != null ? dunderAll.contains(name) : !name.startsWith("_");
  }

  public static boolean isObjectClass(PyClass cls) {
    String qualifiedName = cls.getQualifiedName();
    return PyNames.FQN.OBJECT.equals(qualifiedName) ||
           (qualifiedName == null && PyNames.OBJECT.equals(cls.getName()));
  }

  public static @Nullable PyType getReturnTypeOfMember(PyType type,
                                                       String memberName,
                                                       @Nullable PyExpression location,
                                                       TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<? extends RatedResolveResult> resolveResults = type.resolveMember(memberName, location, AccessDirection.READ,
                                                                                 resolveContext);

    if (resolveResults != null) {
      final List<PyType> types = new ArrayList<>();

      for (RatedResolveResult resolveResult : resolveResults) {
        final PyType returnType = getReturnType(resolveResult.getElement(), context);

        if (returnType != null) {
          types.add(returnType);
        }
      }

      return PyUnionType.unionOrUnknown(types);
    }

    return null;
  }

  private static @Nullable PyType getReturnType(@Nullable PsiElement element, TypeEvalContext context) {
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);

      return getReturnType(type, context);
    }

    return null;
  }

  private static @Nullable PyType getReturnType(@Nullable PyType type, TypeEvalContext context) {
    if (type instanceof PyCallableType) {
      return ((PyCallableType)type).getReturnType(context);
    }

    if (type instanceof PyUnionType) {
      return PyUnionType.toNonWeakType(((PyUnionType)type).map(member -> getReturnType(member, context)));
    }

    return null;
  }

  public static boolean isEmptyFunction(PyFunction function) {
    final PyStatementList statementList = function.getStatementList();
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) {
      return true;
    }
    else if (statements.length == 1) {
      if (isStringLiteral(statements[0]) || isPassOrRaiseOrEmptyReturnOrEllipsis(statements[0])) {
        return true;
      }
    }
    else if (statements.length == 2) {
      if (isStringLiteral(statements[0]) && (isPassOrRaiseOrEmptyReturnOrEllipsis(statements[1]))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPassOrRaiseOrEmptyReturnOrEllipsis(PyStatement stmt) {
    if (stmt instanceof PyPassStatement || stmt instanceof PyRaiseStatement) {
      return true;
    }
    if (stmt instanceof PyReturnStatement && ((PyReturnStatement)stmt).getExpression() == null) {
      return true;
    }
    if (stmt instanceof PyExpressionStatement) {
      final PyExpression expression = ((PyExpressionStatement)stmt).getExpression();
      if (expression instanceof PyEllipsisLiteralExpression) {
        return true;
      }
    }
    return false;
  }

  public static boolean isStringLiteral(@Nullable PyStatement stmt) {
    return PyUtilCore.isStringLiteral(stmt);
  }

  public static @Nullable PyLoopStatement getCorrespondingLoop(PsiElement breakOrContinue) {
    return (PyLoopStatement)PyUtilCore.getCorrespondingLoop(breakOrContinue);
  }

  public static boolean isForbiddenMutableDefault(@Nullable PyTypedElement value, TypeEvalContext context) {
    if (value == null) return false;

    Set<PyClass> pyClasses = PyTypeUtil.toStream(context.getType(value))
      .select(PyClassType.class)
      .filter(clsType -> !clsType.isDefinition())
      .map(PyClassType::getPyClass)
      .toSet();

    if (!pyClasses.isEmpty()) {
      PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(value);
      Set<PyClass> forbiddenClasses = StreamEx
        .of(builtinCache.getListType(), builtinCache.getSetType(), builtinCache.getDictType())
        .nonNull()
        .map(PyClassType::getPyClass)
        .toSet();

      return StreamEx.of(pyClasses)
        .flatMap(pyClass -> StreamEx.of(pyClass).append(pyClass.getAncestorClasses(context)))
        .anyMatch(forbiddenClasses::contains);
    }

    return false;
  }

  public static void addDecorator(PyFunction function, String decorator) {
    final PyDecoratorList currentDecorators = function.getDecoratorList();

    final List<String> decoTexts = new ArrayList<>();
    decoTexts.add(decorator);
    if (currentDecorators != null) {
      for (PyDecorator deco : currentDecorators.getDecorators()) {
        decoTexts.add(deco.getText());
      }
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(function.getProject());
    final PyDecoratorList newDecorators = generator.createDecoratorList(ArrayUtil.toStringArray(decoTexts));

    if (currentDecorators != null) {
      currentDecorators.replace(newDecorators);
    }
    else {
      function.addBefore(newDecorators, function.getFirstChild());
    }
  }

  public static boolean isOrdinaryPackage(PsiDirectory directory) {
    return directory.findFile(PyNames.INIT_DOT_PY) != null;
  }

  public static boolean isNoinspectionComment(PsiComment comment) {
    return SUPPRESS_PATTERN.matcher(comment.getText()).find();
  }

  /**
   * This helper class allows to collect various information about AST nodes composing {@link PyStringLiteralExpression}.
   */
  public static final class StringNodeInfo {
    private final ASTNode myNode;
    private final String myPrefix;
    private final String myQuote;
    private final TextRange myContentRange;

    public StringNodeInfo(ASTNode node) {
      final IElementType nodeType = node.getElementType();
      // TODO Migrate to newer PyStringElement API
      if (!PyTokenTypes.STRING_NODES.contains(nodeType) && nodeType != PyElementTypes.FSTRING_NODE) {
        throw new IllegalArgumentException("Node must be valid Python string literal token, but " + nodeType + " was given");
      }
      myNode = node;
      final String nodeText = node.getText();
      final int prefixLength = PyStringLiteralUtil.getPrefixLength(nodeText);
      myPrefix = nodeText.substring(0, prefixLength);
      myContentRange = PyStringLiteralUtil.getContentRange(nodeText);
      myQuote = nodeText.substring(prefixLength, myContentRange.getStartOffset());
    }

    public StringNodeInfo(PsiElement element) {
      this(element.getNode());
    }

    public ASTNode getNode() {
      return myNode;
    }

    /**
     * @return string prefix, e.g. "UR", "b" etc.
     */
    public String getPrefix() {
      return myPrefix;
    }

    /**
     * @return content of the string node between quotes
     */
    public String getContent() {
      return myContentRange.substring(myNode.getText());
    }

    /**
     * @return <em>relative</em> range of the content (excluding prefix and quotes)
     * @see #getAbsoluteContentRange()
     */
    public TextRange getContentRange() {
      return myContentRange;
    }

    /**
     * @return <em>absolute</em> content range that accounts offset of the {@link #getNode() node} in the document
     */
    public TextRange getAbsoluteContentRange() {
      return getContentRange().shiftRight(myNode.getStartOffset());
    }

    /**
     * @return the first character of {@link #getQuote()}
     */
    public char getSingleQuote() {
      return myQuote.charAt(0);
    }

    public String getQuote() {
      return myQuote;
    }

    public boolean isTripleQuoted() {
      return myQuote.length() == 3;
    }

    /**
     * @return true if string literal ends with starting quote
     */
    public boolean isTerminated() {
      final String text = myNode.getText();
      return text.length() - myPrefix.length() >= myQuote.length() * 2 && text.endsWith(myQuote);
    }

    /**
     * @return true if given string node contains "u" or "U" prefix
     */
    public boolean isUnicode() {
      return PyStringLiteralUtil.isUnicodePrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "r" or "R" prefix
     */
    public boolean isRaw() {
      return PyStringLiteralUtil.isRawPrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "b" or "B" prefix
     */
    public boolean isBytes() {
      return PyStringLiteralUtil.isBytesPrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "f" or "F" prefix
     */
    public boolean isFormatted() {
      return PyStringLiteralUtil.isFormattedPrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "t" or "T" prefix
     */
    public boolean isTemplate() {
      return PyStringLiteralUtil.isTemplatePrefix(myPrefix);
    }

    /**
     * @return true if other string node has the same decorations, i.e. quotes and prefix
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StringNodeInfo info = (StringNodeInfo)o;

      return getQuote().equals(info.getQuote()) &&
             isRaw() == info.isRaw() &&
             isUnicode() == info.isUnicode() &&
             isBytes() == info.isBytes();
    }
  }

  public static final class IterHelper {  // TODO: rename sanely
    private IterHelper() { }

    public static @Nullable PsiNamedElement findName(Iterable<? extends PsiNamedElement> it, String name) {
      PsiNamedElement ret = null;
      for (PsiNamedElement elt : it) {
        if (elt != null) {
          // qualified refs don't match by last name, and we're not checking FQNs here
          if (elt instanceof PyQualifiedExpression && ((PyQualifiedExpression)elt).isQualified()) continue;
          if (name.equals(elt.getName())) { // plain name matches
            ret = elt;
            break;
          }
        }
      }
      return ret;
    }
  }
}
