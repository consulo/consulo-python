// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.magicLiteral.PyMagicLiteralExtensionPoint;
import com.jetbrains.python.impl.magicLiteral.PyMagicLiteralTools;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.cacheBuilder.WordsScanner;
import consulo.language.findUsage.FindUsagesProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * TODO: Create strategies instead of chain of instanceof
 */
@ExtensionImpl
public class PythonFindUsagesProvider implements FindUsagesProvider {
    @Override
    public boolean canFindUsagesFor(PsiElement psiElement) {
        if (PyMagicLiteralTools.couldBeMagicLiteral(psiElement)) {
            return true;
        }
        return (psiElement instanceof PsiNamedElement) || (psiElement instanceof PyReferenceExpression);
    }

    @Override
    public @Nullable String getHelpId(PsiElement psiElement) {
        if (psiElement instanceof PyClass) {
            return "reference.dialogs.findUsages.class";
        }
        if (psiElement instanceof PyFunction) {
            return "reference.dialogs.findUsages.method";
        }
        if (psiElement instanceof PyReferenceExpression || psiElement instanceof PyTargetExpression || psiElement instanceof PyParameter) {
            return "reference.dialogs.findUsages.variable";
        }
        return null;
    }

    @Override
    public String getType(PsiElement element) {
        String literalString = tryFindMagicLiteralString(element, false);
        if (literalString != null) {
            return literalString;
        }

        if (element instanceof PyNamedParameter) {
            return PyLocalize.findUsagesParameter().get();
        }
        if (element instanceof PyFunction) {
            if (((PyFunction) element).getContainingClass() != null) {
                return PyLocalize.findUsagesMethod().get();
            }
            return PyLocalize.findUsagesFunction().get();
        }
        if (element instanceof PyClass) {
            return PyLocalize.findUsagesClass().get();
        }
        if (element instanceof PyReferenceExpression) {
            return PyLocalize.findUsagesVariable().get();
        }
        if (element instanceof PyTargetExpression) {
            PyImportElement importElement = PsiTreeUtil.getParentOfType(element, PyImportElement.class);
            if (importElement != null && importElement.getAsNameElement() == element) {
                return PyLocalize.findUsagesImportedModuleAlias().get();
            }
            return PyLocalize.findUsagesVariable().get();
        }
        if (element instanceof PyKeywordArgument) {
            return PyLocalize.findUsagesKeywordArgument().get();
        }
        return "";
    }

    @Override
    public String getDescriptiveName(PsiElement element) {
        String literalString = tryFindMagicLiteralString(element, true);
        if (literalString != null) {
            return literalString;
        }

        if (element instanceof PsiNamedElement) {
            String name = ((PsiNamedElement) element).getName();
            return name == null ? PyLocalize.findUsagesUnnamed().get() : name;
        }
        if (element instanceof PyReferenceExpression) {
            String referencedName = ((PyReferenceExpression) element).getReferencedName();
            if (referencedName == null) {
                return PyLocalize.findUsagesUnnamed().get();
            }
            return referencedName;
        }
        return "";
    }

    @Override
    public String getNodeText(PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }

    @Override
    public WordsScanner getWordsScanner() {
        return new PyWordsScanner();
    }

    /**
     * Finds text to display to user for element if element is magic literal
     *
     * @param element     element to check
     * @param obtainValue display element value (will display element type otherwise)
     * @return text (if found) or null
     */
    private static @Nullable String tryFindMagicLiteralString(PsiElement element, boolean obtainValue) {
        if (element instanceof PyStringLiteralExpression) {
            PyMagicLiteralExtensionPoint point = PyMagicLiteralTools.getPoint((PyStringLiteralExpression) element);
            if (point != null) {
                if (obtainValue) {
                    return ((StringLiteralExpression) element).getStringValue();
                }
                else {
                    return point.getLiteralType();
                }
            }
        }
        return null;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
