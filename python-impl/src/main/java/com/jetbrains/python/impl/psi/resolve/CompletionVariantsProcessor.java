// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.language.icon.IconDescriptorUpdaters;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.impl.codeInsight.completion.PyClassInsertHandler;
import com.jetbrains.python.impl.codeInsight.completion.PyFunctionInsertHandler;
import com.jetbrains.python.impl.codeInsight.completion.PyParameterizedTypeInsertHandler;
import com.jetbrains.python.impl.codeInsight.completion.PythonCompletionWeigher;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementInfo;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyDescriptorTypeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public class CompletionVariantsProcessor extends VariantsProcessor {

  private final Map<String, LookupElement> myVariants = new HashMap<>();

  private final boolean mySuppressParentheses;

  private @Nullable TypeEvalContext myCompletionTypeEvalContext;

  private @Nullable TypeEvalContext myAnalysisTypeEvalContext;

  public CompletionVariantsProcessor(PsiElement context) {
    super(context);
    mySuppressParentheses = false;
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Predicate<PsiElement> nodeFilter,
                                     @Nullable Predicate<String> nameFilter) {
    super(context, nodeFilter, nameFilter);
    mySuppressParentheses = false;
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Predicate<PsiElement> nodeFilter,
                                     @Nullable Predicate<String> nameFilter,
                                     boolean plainNamesOnly,
                                     boolean suppressParentheses) {
    super(context, nodeFilter, nameFilter, plainNamesOnly);
    mySuppressParentheses = suppressParentheses;
  }

  private LookupElement setupItem(LookupElementBuilder item) {
    PyCodeInsightCounters.inc(PyCodeInsightCounters.Counter.COMPLETION_LOOKUP_ITEMS);
    final PsiElement element = item.getPsiElement();
    final boolean decoratedIntoValue = element instanceof PyFunction function && isDecoratedIntoValue(function);
    if (decoratedIntoValue) {
      item = item.withIcon(PlatformIconGroup.nodesVariable());
    }
    if (!myPlainNamesOnly && element != null) {
      final TypeEvalContext context = getCompletionTypeEvalContext(element.getProject());

      if (myContext != null && PyParameterizedTypeInsertHandler.isCompletingParameterizedType(element, myContext, context)) {
        item = item.withInsertHandler(PyParameterizedTypeInsertHandler.INSTANCE);
      }
      else if (!mySuppressParentheses && !decoratedIntoValue &&
               element instanceof PyFunction && ((PyFunction)element).getProperty() == null &&
               !PyKnownDecoratorUtil.hasUnknownDecorator((PyFunction)element, context) &&
               !isSingleArgDecoratorCall(myContext, (PyFunction)element)) {
        item = item.withInsertHandler(PyFunctionInsertHandler.INSTANCE);
        final List<PyCallableParameter> parameters = ((PyFunction)element).getParameters(context);
        final String params = StringUtil.join(parameters, PyCallableParameter::getName, ", ");
        item = item.withTailText("(" + params + ")");
      }
      else if (element instanceof PyClass) {
        item = item.withInsertHandler(PyClassInsertHandler.INSTANCE);
      }
    }
    String source = null;
    if (element != null) {
      PyClass cls = null;

      if (element instanceof PyFunction) {
        cls = ((PyFunction)element).getContainingClass();
      }
      else if (element instanceof PyTargetExpression expr) {
        if (expr.isQualified() || ScopeUtil.getScopeOwner(expr) instanceof PyClass) {
          cls = expr.getContainingClass();
        }
      }
      else if (element instanceof PyClass) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
        if (owner instanceof PyClass) {
          cls = (PyClass)owner;
        }
      }

      if (cls != null) {
        source = cls.getName();
      }
      else if (myContext == null || !PyUtil.inSameFile(myContext, element)) {
        final PsiFileSystemItem fileSystemItem = PyPsiUtils.getFileSystemItem(element);
        final QualifiedName path = QualifiedNameFinder.findShortestImportableQName(fileSystemItem);
        if (path != null) {
          source = ObjectUtil.chooseNotNull(QualifiedNameFinder.canonizeQualifiedName(fileSystemItem, path, null), path).toString();
        }
      }
    }
    if (source != null) {
      item = item.withTypeText(source);
    }

    final PsiElement parent = myContext != null ? myContext.getParent() : null;
    if (parent instanceof PyKeywordArgument) {
      final String keyword = ((PyKeywordArgument)parent).getKeyword();
      if (item.getLookupString().equals(keyword)) {
        return PrioritizedLookupElement.withPriority(item, PythonCompletionWeigher.PRIORITY_WEIGHT);
      }
    }

    PyCompletionMlElementInfo info = PyCompletionMlElementInfo.fromElement(element);
    item.putUserData(PyCompletionMlElementInfo.getKey(), info);
    return item;
  }

  private TypeEvalContext getCompletionTypeEvalContext(Project project) {
    if (myCompletionTypeEvalContext == null) {
      myCompletionTypeEvalContext = TypeEvalContext.codeCompletion(project, getOriginFile());
    }
    return myCompletionTypeEvalContext;
  }

  /**
   * The context reads only the stubs of the other files. The completion context can load their trees.
   */
  private TypeEvalContext getAnalysisTypeEvalContext(Project project) {
    if (myAnalysisTypeEvalContext == null) {
      myAnalysisTypeEvalContext = TypeEvalContext.codeAnalysis(project, getOriginFile());
    }
    return myAnalysisTypeEvalContext;
  }

  private @Nullable PsiFile getOriginFile() {
    return myContext != null ? myContext.getContainingFile() : null;
  }

  /**
   * Checks that the decorators of {@code function} make it an object that is not callable, for example {@code @SomeClass def f(): ...}.
   * Completion then shows the name as a variable, not as a function.
   * <p>
   * For a method, the check applies {@code __get__} of the decorated object for an access through an instance.
   */
  private boolean isDecoratedIntoValue(PyFunction function) {
    if (function.getDecoratorList() == null) return false;
    final TypeEvalContext context = getAnalysisTypeEvalContext(function.getProject());
    if (!PyKnownDecoratorUtil.hasUnknownDecorator(function, context)) return false;
    final Ref<PyType> typeRef = PyReferenceExpressionImpl.getReferenceTypeFromProviders(function, context, null);
    PyType type = typeRef != null ? typeRef.get() : null;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass != null && type != null) {
      final PyClassType noneType = PyBuiltinCache.getInstance(function).getNoneType();
      if (noneType == null) return false;
      type = PyDescriptorTypeUtil.applyDescriptorGet(new PyClassTypeImpl(containingClass, false), type, noneType, context);
    }
    return type instanceof PyClassLikeType classType && !classType.isCallable();
  }

  private static boolean isSingleArgDecoratorCall(@Nullable PsiElement elementInCall, PyFunction callee) {
    // special case hack to avoid the need of patching generator3.py
    final PyClass containingClass = callee.getContainingClass();
    if (containingClass != null && PyNames.PROPERTY.equals(containingClass.getName()) &&
        PyBuiltinCache.getInstance(elementInCall).isBuiltin(containingClass)) {
      return true;
    }

    if (callee.getParameterList().getParameters().length > 1) {
      return false;
    }
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(elementInCall, PyDecorator.class);
    if (decorator == null) {
      return false;
    }
    return PsiTreeUtil.isAncestor(decorator.getCallee(), elementInCall, false);
  }

  public LookupElement [] getResult() {
    final Collection<LookupElement> variants = myVariants.values();
    return variants.toArray(LookupElement.EMPTY_ARRAY);
  }

  public List<LookupElement> getResultList() {
    return new ArrayList<>(myVariants.values());
  }

  @Override
  protected void addElement(String name, PsiElement element) {
    markAsProcessed(name);
    myVariants.put(name, setupItem(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(IconDescriptorUpdaters.getIcon(element, 0))));
  }

  @Override
  protected void addImportedElement(String name, PyElement element) {
    Image icon = IconDescriptorUpdaters.getIcon(element, 0);
    // things like PyTargetExpression cannot have a general icon, but here we only have variables
    if (icon == null) icon = PlatformIconGroup.nodesVariable();
    markAsProcessed(name);
    myVariants.put(name, setupItem(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(icon)));
  }
}
