package com.jetbrains.python.psi.impl;

import javax.swing.Icon;

import com.intellij.ide.IconDescriptorUpdaters;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;

/**
 * @author yole
 */
public abstract class PyPresentableElementImpl<T extends StubElement> extends PyBaseElementImpl<T> implements PsiNamedElement {
  public PyPresentableElementImpl(ASTNode astNode) {
    super(astNode);
  }

  protected PyPresentableElementImpl(final T stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return name != null ? name : "<none>";
      }

      public String getLocationString() {
        return getElementLocation();
      }

      public Icon getIcon(final boolean open) {
        return IconDescriptorUpdaters.getIcon(PyPresentableElementImpl.this, 0);
      }
    };
  }

  protected String getElementLocation() {
    return "(" + getPackageForFile(getContainingFile()) + ")";
  }

  public static String getPackageForFile(final PsiFile containingFile) {
    final VirtualFile vFile = containingFile.getVirtualFile();

    if (vFile != null) {
      final String importableName = QualifiedNameFinder.findShortestImportableName(containingFile, vFile);
      if (importableName != null) {
        return importableName;
      }
    }
    return "";
  }
}
