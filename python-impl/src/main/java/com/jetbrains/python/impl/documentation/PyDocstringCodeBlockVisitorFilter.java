package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.doctest.PyDocstringCodeBlockLanguageDialect;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import com.jetbrains.python.impl.validation.PySyntaxAnnotator;
import com.jetbrains.python.impl.validation.UnsupportedFeatures;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Controls which visitors run for a plain Python code block injected into a docstring.
 * <p>
 * An inspection runs only when {@link PyDocumentationSettings#isInspectDocstring} is on.
 * Syntax and unsupported-feature annotators are always disabled for these code blocks.
 * Doctest fragments ({@link com.jetbrains.python.impl.documentation.doctest.PyDoctestLanguageDialect}) are handled separately by {@code PyDoctestVisitorFilter}.
 */
@ExtensionImpl
public final class PyDocstringCodeBlockVisitorFilter implements PythonVisitorFilter {
    private final Set<Class<?>> disabledAnnotators;

    public PyDocstringCodeBlockVisitorFilter() {
        Set<Class<?>> set = new HashSet<>(PyInjectedPythonCodeVisitors.disabledAnnotators);
        set.add(PySyntaxAnnotator.class);
        set.add(UnsupportedFeatures.class);
        disabledAnnotators = set;
    }

    @Override
    public boolean isSupported(Class visitorClass, PsiFile file) {
        if (disabledAnnotators.contains(visitorClass) || PyInjectedPythonCodeVisitors.disabledInspections.contains(visitorClass)) {
            return false;
        }

        boolean isInspection = LocalInspectionTool.class.isAssignableFrom(visitorClass);
        if (!isInspection) {
            return true;
        }

        PyStringLiteralExpression docstring = getDocstringByHost(file);
        if (docstring == null) {
            return true;
        }
        return inspectsDocstringEnabled(docstring);
    }

    private static @Nullable PyStringLiteralExpression getDocstringByHost(PsiFile file) {
        if (!file.getLanguage().is(PyDocstringCodeBlockLanguageDialect.getInstance())) {
            return null;
        }
        return DocStringUtil.getDocstringInjectionHost(file);
    }

    private static boolean inspectsDocstringEnabled(PyStringLiteralExpression docstring) {
        Module module = ModuleUtilCore.findModuleForPsiElement(docstring);
        return PyDocumentationSettings.getInstance(module).isInspectDocstring();
    }

    @Override
    public Language getLanguage() {
        return PyDocstringCodeBlockLanguageDialect.getInstance();
    }
}
