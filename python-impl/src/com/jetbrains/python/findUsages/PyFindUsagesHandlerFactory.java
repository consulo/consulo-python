package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PyClass ||
           (element instanceof PyFile && PyUtil.isPackage((PyFile)element)) ||
           element instanceof PyImportedModule ||
           element instanceof PyFunction;
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof PyImportedModule) {
      final PsiElement resolved = ((PyImportedModule)element).resolve();
      if (resolved != null) {
        element = resolved;
      }
    }
    if (element instanceof PsiFileSystemItem) {
      return new PyModuleFindUsagesHandler((PsiFileSystemItem)element);
    }
    if (element instanceof PyFunction) {
      if (!forHighlightUsages) {
        final Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction)element, true).findAll();
        if (superMethods.size() > 0) {
          final PsiElement next = superMethods.iterator().next();
          // TODO should do this for Jython functions overriding Java methods too
          if (next instanceof PyFunction && !isInObject((PyFunction)next)) {
            int rc = Messages.showYesNoCancelDialog(element.getProject(), "Method " +
                                                                          ((PyFunction)element).getName() +
                                                                          " overrides method of class " +
                                                                          ((PyFunction)next).getContainingClass().getName() +
                                                                          ".\nDo you want to find usages of the base method?",  "Find Usages", Messages.getQuestionIcon());
            if (rc == 0) {
              List<PsiElement> allMethods = new ArrayList<PsiElement>();
              allMethods.add(element);
              allMethods.addAll(superMethods);
              return new PyFunctionFindUsagesHandler(element, allMethods);
            }
            if (rc == 1) {
              return new PyFunctionFindUsagesHandler(element);
            }
            return FindUsagesHandler.NULL_HANDLER;
          }
        }

      }
      return new PyFunctionFindUsagesHandler(element);
    }
    if (element instanceof PyClass) {
      return new PyClassFindUsagesHandler((PyClass)element);
    }
    return null;
  }

  private static boolean isInObject(PyFunction fun) {
    final PyClass containingClass = fun.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    return (PyNames.FAKE_OLD_BASE.equals(containingClass.getName()) ||
            (PyNames.OBJECT.equals(containingClass.getName()) && PyBuiltinCache.getInstance(fun).hasInBuiltins(containingClass)));
  }
}
