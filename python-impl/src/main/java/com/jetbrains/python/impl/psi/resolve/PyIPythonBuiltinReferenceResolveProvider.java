// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyEmptyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.QualifiedName;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.fromFoothold;
import static com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.resolveTopLevelMember;

@ExtensionImpl
public final class PyIPythonBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {

  private final Pattern underscoreRegex = Pattern.compile("_[0-9]*");

  @Override
  public List<RatedResolveResult> resolveName(PyQualifiedExpression element, TypeEvalContext context) {
    if (!needToResolve(context)) return Collections.emptyList();
    String name = element.getReferencedName();
    if (name == null) return Collections.emptyList();
    if (underscoreRegex.matcher(name).matches()) {
      return resolveOut(element, context);
    }
    switch (name) {
      case IPythonBuiltinConstants.DISPLAY:
        return resolveDisplay(element);
      case IPythonBuiltinConstants.GET_IPYTHON:
        return resolveGetIPython(element);
      case IPythonBuiltinConstants.IN:
        return resolveIn(element, context);
      case IPythonBuiltinConstants.OUT:
        return resolveOut(element, context);
      case IPythonBuiltinConstants.DOUBLE_UNDERSCORE:
        return resolveOut(element, context);
      case IPythonBuiltinConstants.TRIPLE_UNDERSCORE:
        return resolveOut(element, context);
      default:
        break;
    }

    if (IPythonBuiltinConstants.MAGICS_LIST.contains(name)
        && element.getParent() instanceof PyExpressionStatement
        && isAutomagicOn(element)
    ) {
      return resolveMagics(element, context);
    }
    return Collections.emptyList();
  }

  /**
   * We resolve IPython built-ins in PyCharm PRO in Jupyter files only.
   */
  private static boolean needToResolve(TypeEvalContext context) {
    PsiFile psiFile = context.getOrigin();
    if (psiFile == null) return false;
    return isJupyterFile(psiFile);
  }

  private static boolean isJupyterFile(PsiFile element) {
    VirtualFile virtualFile = element.getVirtualFile();
    return virtualFile != null && "ipynb".equals(virtualFile.getExtension());
  }

  private static List<RatedResolveResult> resolveDisplay(PyQualifiedExpression element) {
    PyQualifiedNameResolveContext resolveContext = fromFoothold(element);
    PsiElement displayPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.DISPLAY_DOTTED_PATH_NEW),
                                                  resolveContext);
    if (displayPsi == null) {
      displayPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.DISPLAY_DOTTED_PATH_OLD),
                                         resolveContext);
    }
    return ResolveResultList.to(displayPsi);
  }

  private static List<RatedResolveResult> resolveGetIPython(PyQualifiedExpression element) {
    PsiElement getIPythonPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.GET_IPYTHON_DOTTED_PATH),
                                                     fromFoothold(element));
    return ResolveResultList.to(getIPythonPsi);
  }

  private static List<RatedResolveResult> resolveIn(PyQualifiedExpression element, TypeEvalContext context) {
    PyClass historyClass = getPyClassByDottedPath(IPythonBuiltinConstants.HISTORY_MANAGER_DOTTED_PATH, fromFoothold(element));
    if (historyClass == null) return Collections.emptyList();
    PsiElement inHistDictPsi = historyClass.findClassAttribute(IPythonBuiltinConstants.IN_HIST_DICT, false, context);
    return ResolveResultList.to(inHistDictPsi);
  }

  private static List<RatedResolveResult> resolveOut(PyQualifiedExpression element, TypeEvalContext context) {
    PyClass historyClass = getPyClassByDottedPath(IPythonBuiltinConstants.HISTORY_MANAGER_DOTTED_PATH, fromFoothold(element));
    if (historyClass == null) return Collections.emptyList();
    PsiElement outHistDictPsi = historyClass.findClassAttribute(IPythonBuiltinConstants.OUT_HIST_DICT, false, context);
    return ResolveResultList.to(outHistDictPsi);
  }

  private static List<RatedResolveResult> resolveMagics(PyQualifiedExpression element, TypeEvalContext context) {
    String name = element.getReferencedName();
    if (name == null) return Collections.emptyList();
    String dottedPath = getMagicDottedPathString(name);
    if (dottedPath == null) return Collections.emptyList();
    PyClass magicsClass = getPyClassByDottedPath(dottedPath, fromFoothold(element));
    if (magicsClass == null) return Collections.emptyList();
    PsiElement magicPsi = magicsClass.findMethodByName(name, false, context);
    return ResolveResultList.to(magicPsi);
  }

  private static @Nullable String getMagicDottedPathString(String name) {
    if (IPythonBuiltinConstants.AUTOMAGIC_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_AUTOMAGIC_DOTTED_PATH;
    if (IPythonBuiltinConstants.BASIC_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_BASIC_DOTTED_PATH;
    if (IPythonBuiltinConstants.CODE_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_CODE_DOTTED_PATH;
    if (IPythonBuiltinConstants.CONFIG_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_CONFIG_DOTTED_PATH;
    if (IPythonBuiltinConstants.EXECUTION_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_EXECUTION_DOTTED_PATH;
    if (IPythonBuiltinConstants.EXTENSION_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_EXTENSION_DOTTED_PATH;
    if (IPythonBuiltinConstants.HISTORY_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_HISTORY_DOTTED_PATH;
    if (IPythonBuiltinConstants.LOGGING_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_LOGGING_DOTTED_PATH;
    if (IPythonBuiltinConstants.NAMESPACE_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_NAMESPACE_DOTTED_PATH;
    if (IPythonBuiltinConstants.OS_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_OS_DOTTED_PATH;
    if (IPythonBuiltinConstants.PACKAGING_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_PACKAGING_DOTTED_PATH;
    if (IPythonBuiltinConstants.PYLAB_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_PYLAB_DOTTED_PATH;
    if (IPythonBuiltinConstants.ASYNC_MAGICS.contains(name)) return IPythonBuiltinConstants.MAGIC_ASYNC_DOTTED_PATH;
    return null;
  }

  private static @Nullable PyClass getPyClassByDottedPath(String dottedPath, PyQualifiedNameResolveContext context) {
    return resolveTopLevelMember(QualifiedName.fromDottedString(dottedPath), context) instanceof PyClass pyClass ? pyClass : null;
  }

  private static boolean isAutomagicOn(PyQualifiedExpression element) {
    // element.parent.siblings(forward = false, withSelf = false)
    for (PsiElement sib = element.getParent().getPrevSibling(); sib != null; sib = sib.getPrevSibling()) {
      if (sib instanceof PyEmptyExpression) {
        if (sib.getText().equals("%automagic 0")) {
          return false;
        }
        else if (sib.getText().equals("%automagic 1")) {
          return true;
        }
      }
      if (sib instanceof PyExpressionStatement && sib.getNextSibling() instanceof PsiWhiteSpace) {
        PsiElement[] children = sib.getChildren();
        PsiElement nextStatement = sib.getNextSibling().getNextSibling();
        if (children.length == 2 && children[0] instanceof PyReferenceExpression && children[children.length - 1] instanceof PsiErrorElement
            && children[0].getText().equals("automagic")
            && nextStatement instanceof PyExpressionStatement
            && nextStatement.getChildren().length == 1 && nextStatement.getChildren()[0] instanceof PyNumericLiteralExpression
        ) {
          if (nextStatement.getText().equals("1")) {
            return true;
          }
          if (nextStatement.getText().equals("0")) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
