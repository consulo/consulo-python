// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralFileReferenceSet;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ProcessingContext;
import consulo.platform.Platform;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.fromFoothold;
import static com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.resolveTopLevelMember;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;
import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * Contributes file path references for Python string literals where it seems appropriate based on heuristics.
 * <p>
 * References are soft: used only for code completion and ignored during code inspection.
 */
// JB: order="before filePath" - we run it before FilePathCompletionContributor (id="filePath") to get help text about full path
// results in the extended completion
@ExtensionImpl(order = "before filePath")
public class PySoftFileReferenceContributor extends PsiReferenceContributor {
  private static final Set<String> FILE_NAME_PATTERNS = new LinkedHashSet<>(List.of(
    "path",
    "file",
    "filename",
    "filepath",
    "pathname",
    "src",
    "dst",
    "dir"
  ));

  private static boolean matchesPathNamePattern(String name) {
    String[] nameParts = name.split("_");
    return Arrays.stream(nameParts).anyMatch(it -> FILE_NAME_PATTERNS.contains(it.toLowerCase(Locale.getDefault())));
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    PsiElementPattern.Capture<PyStringLiteralExpression> stringLiteral = psiElement(PyStringLiteralExpression.class);
    PsiElementPattern.Capture<PsiElement> pattern = psiElement()
      .andOr(stringLiteral.with(HardCodedCalleeName.INSTANCE),
             stringLiteral.with(AssignmentMatchingNamePattern.INSTANCE),
             stringLiteral.with(KeywordArgumentMatchingNamePattern.INSTANCE),
             stringLiteral.with(CallArgumentMatchingParameterNamePattern.INSTANCE),
             stringLiteral.with(CallArgumentMatchingParameterType.INSTANCE));
    registrar.registerReferenceProvider(pattern, createSoftFileReferenceProvider());
  }

  public PsiReferenceProvider createSoftFileReferenceProvider() {
    return PySoftFileReferenceProvider.INSTANCE;
  }

  /**
   * Matches string literals used in assignment statements where the assignment target has a name that has something about files or paths.
   */
  private static final class AssignmentMatchingNamePattern extends PatternCondition<PyStringLiteralExpression> {
    static final AssignmentMatchingNamePattern INSTANCE = new AssignmentMatchingNamePattern();

    private AssignmentMatchingNamePattern() {
      super("assignmentMatchingPattern");
    }

    @Override
    public boolean accepts(PyStringLiteralExpression expr, @Nullable ProcessingContext context) {
      if (!(expr.getParent() instanceof PyAssignmentStatement assignment)) return false;
      return Arrays.stream(assignment.getTargets())
        .filter(PyTargetExpression.class::isInstance)
        .map(it -> ((PyTargetExpression)it).getName())
        .filter(Objects::nonNull)
        .anyMatch(PySoftFileReferenceContributor::matchesPathNamePattern);
    }
  }

  /**
   * Matches string literals used as function arguments where the corresponding function parameter has a name that has something about
   * files or paths.
   */
  private static final class CallArgumentMatchingParameterNamePattern extends PatternCondition<PyStringLiteralExpression> {
    static final CallArgumentMatchingParameterNamePattern INSTANCE = new CallArgumentMatchingParameterNamePattern();

    private CallArgumentMatchingParameterNamePattern() {
      super("callArgumentMatchingPattern");
    }

    @Override
    public boolean accepts(PyStringLiteralExpression expr, @Nullable ProcessingContext context) {
      if (!(expr.getParent() instanceof PyArgumentList argList)) return false;
      if (!(argList.getParent() instanceof PyCallExpression callExpr)) return false;
      TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(expr.getProject());
      PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);

      return callExpr.multiResolveCallee(resolveContext)
        .stream()
        // Fail-fast check
        .filter(callableType -> {
          List<PyCallableParameter> parameters = callableType.getParameters(typeEvalContext);
          List<String> parameterNames = parameters != null
                                        ? parameters.stream().map(PyCallableParameter::getName).filter(Objects::nonNull).toList()
                                        : List.of();
          return parameterNames.stream().anyMatch(PySoftFileReferenceContributor::matchesPathNamePattern);
        })
        .anyMatch(it -> {
          PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callExpr, it, typeEvalContext);
          PyCallableParameter parameter = mapping.getMappedParameters().get(expr);
          String parameterName = parameter != null ? parameter.getName() : null;
          if (parameterName == null) return false;
          return matchesPathNamePattern(parameterName);
        });
    }
  }

  private static final class CallArgumentMatchingParameterType extends PatternCondition<PyStringLiteralExpression> {
    static final CallArgumentMatchingParameterType INSTANCE = new CallArgumentMatchingParameterType();

    private CallArgumentMatchingParameterType() {
      super("callArgumentMatchingPattern");
    }

    @Override
    public boolean accepts(PyStringLiteralExpression expr, @Nullable ProcessingContext context) {
      if (!(expr.getParent() instanceof PyArgumentList argList)) return false;
      if (!(argList.getParent() instanceof PyCallExpression callExpr)) return false;

      PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(expr);
      LanguageLevel languageLevel = LanguageLevel.forElement(expr);
      List<PyType> bytesOrUnicodeMembers = new ArrayList<>();
      PyType bytesType = builtinCache.getBytesType(languageLevel);
      if (bytesType != null) bytesOrUnicodeMembers.add(bytesType);
      PyType unicodeType = builtinCache.getUnicodeType(languageLevel);
      if (unicodeType != null) bytesOrUnicodeMembers.add(unicodeType);
      PyType bytesOrUnicodeType = PyUnionType.unionOrUnknown(bytesOrUnicodeMembers);
      if (bytesOrUnicodeType == null) return false;

      TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(expr.getProject());

      if (!(resolveTopLevelMember(QualifiedName.fromComponents("os", "PathLike"), fromFoothold(expr)) instanceof PyTypedElement osPathLike)) {
        return false;
      }
      PyType osPathLikeType = typeEvalContext.getType(osPathLike);
      if (osPathLikeType == null) return false;

      List<PyType> argumentTypes = callExpr.multiResolveCallee(PyResolveContext.defaultContext(typeEvalContext))
        .stream()
        .map(it -> {
          PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callExpr, it, typeEvalContext);
          PyCallableParameter type = mapping.getMappedParameters().get(expr);
          if (type == null) {
            return null;
          }
          PyType t = type.getArgumentType(typeEvalContext);
          return isUnknown(t) ? null : t;
        })
        .filter(Objects::nonNull)
        .toList();

      // We can't use PyTypeChecker.match directly because the type `str | PathLike` is considered incompatible
      // with neither str nor PathLike (strict union semantics).
      return argumentTypes.stream().anyMatch(it -> allowsValuesCompatibleWith(it, bytesOrUnicodeType, typeEvalContext)) &&
             argumentTypes.stream().anyMatch(it -> allowsValuesCompatibleWith(it, osPathLikeType, typeEvalContext));
    }

    private static boolean allowsValuesCompatibleWith(PyType type, PyType superType, TypeEvalContext typeEvalContext) {
      return PyTypeUtil.toStream(type).anyMatch(it -> !isAnyOrUnknown(it) && PyTypeChecker.match(superType, it, typeEvalContext));
    }
  }

  /**
   * Matches string literals used as function keyword arguments where the keyword has a name that has something about files or paths.
   */
  private static final class KeywordArgumentMatchingNamePattern extends PatternCondition<PyStringLiteralExpression> {
    static final KeywordArgumentMatchingNamePattern INSTANCE = new KeywordArgumentMatchingNamePattern();

    private KeywordArgumentMatchingNamePattern() {
      super("keywordArgumentMatchingPattern");
    }

    @Override
    public boolean accepts(PyStringLiteralExpression expr, @Nullable ProcessingContext context) {
      if (!(expr.getParent() instanceof PyKeywordArgument keywordArgument)) return false;
      PsiElement keywordArgumentParent = keywordArgument.getParent();
      if (!((keywordArgumentParent != null ? keywordArgumentParent.getParent() : null) instanceof PyCallExpression)) return false;
      String keyword = keywordArgument.getKeyword();
      if (keyword == null) return false;
      return matchesPathNamePattern(keyword);
    }
  }

  /**
   * Matches string literals used as function arguments for the hard-coded set of function known to work with files.
   * <p>
   * It's a last resort where we cannot guess that the string literal is a file path by other means.
   */
  private static final class HardCodedCalleeName extends PatternCondition<PyStringLiteralExpression> {
    static final HardCodedCalleeName INSTANCE = new HardCodedCalleeName();

    private record Pattern(String fullName, int position, boolean isBuiltin) {
      Pattern(String fullName, int position) {
        this(fullName, position, false);
      }

      QualifiedName qualifiedName() {
        return QualifiedName.fromDottedString(fullName);
      }
    }

    private static final List<Pattern> PATTERNS = List.of(
      new Pattern("open", 0, true), // could be covered by CallArgumentMatchingParameterType in Py3+
      new Pattern("os.walk", 0), // could be covered by CallArgumentMatchingParameterType in Py3+
      new Pattern("pandas.read_csv", 0)
    );
    private static final Map<String, Pattern> SIMPLE_NAMES = new LinkedHashMap<>();
    private static final Map<QualifiedName, Pattern> QUALIFIED_NAMES = new LinkedHashMap<>();

    static {
      for (Pattern pattern : PATTERNS) {
        SIMPLE_NAMES.put(pattern.qualifiedName().getLastComponent(), pattern);
        QUALIFIED_NAMES.put(pattern.qualifiedName(), pattern);
      }
    }

    private HardCodedCalleeName() {
      super("hardCodedCalleeName");
    }

    @Override
    public boolean accepts(PyStringLiteralExpression expr, @Nullable ProcessingContext context) {
      if (!(expr.getParent() instanceof PyArgumentList argList)) return false;
      if (!(argList.getParent() instanceof PyCallExpression callExpr)) return false;
      if (!(callExpr.getCallee() instanceof PyReferenceExpression callee)) return false;

      // Fail-fast check
      Pattern simplePattern = SIMPLE_NAMES.get(callee.getName());
      if (simplePattern == null) return false;
      PyExpression[] arguments = argList.getArguments();
      if (simplePattern.isBuiltin() && Arrays.asList(arguments).indexOf(expr) == simplePattern.position()) return true;

      List<QualifiedName> qNames = PyResolveUtil.resolveImportedElementQNameLocally(callee);
      Pattern pattern = qNames.isEmpty() ? null : QUALIFIED_NAMES.get(qNames.get(0));
      if (pattern == null) return false;
      return pattern.position() >= 0 && pattern.position() < arguments.length && arguments[pattern.position()] == expr;
    }
  }

  private static final class PySoftFileReferenceProvider extends PsiReferenceProvider {
    static final PySoftFileReferenceProvider INSTANCE = new PySoftFileReferenceProvider();

    @Override
    public boolean acceptsTarget(PsiElement target) {
      return target instanceof PsiFileSystemItem;
    }

    @Override
    public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
      if (!(element instanceof PyStringLiteralExpression expr)) return PsiReference.EMPTY_ARRAY;
      return new PySoftFileReferenceSet(expr, this).getAllReferences();
    }
  }

  /**
   * A soft file reference in Python string literals.
   * <ul>
   * <li>It's used only for code completion and ignored during code inspections</li>
   * <li>It understands {@code ~} as an alias for the user home path</li>
   * </ul>
   */
  public static class PySoftFileReferenceSet extends PyStringLiteralFileReferenceSet {
    public PySoftFileReferenceSet(PyStringLiteralExpression element, PsiReferenceProvider provider) {
      super(element.getStringValue(), element,
            ElementManipulators.getValueTextRange(element).getStartOffset(),
            provider, !Platform.current().os().isWindows(), false, null);
    }

    @Override
    protected boolean isSoft() {
      return true;
    }

    @Override
    public FileReference createFileReference(TextRange range, int index, String text) {
      return doCreateFileReference(range, index, expandUserHome(text));
    }

    public FileReference doCreateFileReference(TextRange range, int index, String expandedText) {
      return super.createFileReference(range, index, expandedText);
    }

    @Override
    public boolean isAbsolutePathReference() {
      return super.isAbsolutePathReference() || getPathString().startsWith("~");
    }

    private static String expandUserHome(String text) {
      return switch (text) {
        case "~" -> Platform.current().user().homePath().toString();
        default -> text;
      };
    }
  }
}
