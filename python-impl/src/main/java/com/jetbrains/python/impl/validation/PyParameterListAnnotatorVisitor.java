// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks for anomalies in parameter lists of function declarations.
 */
public class PyParameterListAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyParameterListAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyParameterList(PyParameterList paramlist) {
        LanguageLevel languageLevel = LanguageLevel.forElement(paramlist);

        ParamHelper.walkDownParamArray(
            paramlist.getParameters(),
            new ParamHelper.ParamVisitor() {
                final Set<String> parameterNames = new HashSet<>();
                boolean hadPositionalContainer = false;
                boolean hadKeywordContainer = false;
                boolean hadDefaultValue = false;
                boolean hadKeyword = false;
                boolean hadSlash = false;
                boolean hadSingleStar = false;
                boolean hadParamsAfterSingleStar = false;
                int inTuple = 0;

                @Override
                public void visitNamedParameter(PyNamedParameter parameter, boolean first, boolean last) {
                    String name = parameter.getName();
                    if (!hadKeyword && name != null && !parameter.isSelf() && parameter.getProtectionLevel() != ProtectionLevel.PRIVATE) {
                        hadKeyword = true;
                    }
                    else if (hadKeyword && !hadPositionalContainer && !hadSingleStar && name != null && !hadSlash
                        && parameter.getProtectionLevel() == ProtectionLevel.PRIVATE) {
                        myHolder
                            .newAnnotation(HighlightSeverity.WARNING, PyLocalize.annPositionalOnlyParamAfterKeyword().get())
                            .range(parameter)
                            .create();
                    }
                    if (parameterNames.contains(parameter.getName())) {
                        myHolder.markError(parameter, PyLocalize.annDuplicateParamName().get());
                    }
                    parameterNames.add(parameter.getName());
                    if (parameter.isPositionalContainer()) {
                        if (hadKeywordContainer) {
                            myHolder.markError(parameter, PyLocalize.annStarredParamAfterKwparam().get());
                        }
                        if (hadSingleStar) {
                            myHolder.markError(parameter, PyLocalize.annMultipleArgs().get());
                        }

                        if (hadPositionalContainer) {
                            myHolder.markError(parameter, PyLocalize.annMultipleArgs().get());
                        }
                        hadPositionalContainer = true;
                    }
                    else if (parameter.isKeywordContainer()) {
                        if (hadKeywordContainer) {
                            myHolder.markError(parameter, PyLocalize.annMultipleKwargs().get());
                        }
                        hadKeywordContainer = true;

                        if (hadSingleStar && !hadParamsAfterSingleStar) {
                            myHolder.markError(parameter, PyLocalize.annNamedParametersAfterStar().get());
                        }
                    }
                    else {
                        if (hadSingleStar) {
                            hadParamsAfterSingleStar = true;
                        }
                        if (hadPositionalContainer && languageLevel.isPython2()) {
                            myHolder.markError(parameter, PyLocalize.annRegularParamAfterVararg().get());
                        }
                        else if (hadKeywordContainer) {
                            myHolder.markError(parameter, PyLocalize.annRegularParamAfterKeyword().get());
                        }
                        if (parameter.hasDefaultValue()) {
                            hadDefaultValue = true;
                        }
                        else {
                            if (hadDefaultValue && !hadSingleStar && (languageLevel.isPython2() || !hadPositionalContainer) && inTuple == 0) {
                                myHolder.markError(parameter, PyLocalize.annNonDefaultParamAfterDefault().get());
                            }
                        }
                    }
                }

                @Override
                public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
                    inTuple++;
                    if (languageLevel.isPy3K()) {
                        myHolder.markError(param, PyLocalize.annTuplePy3().get());
                    }
                    else if (!param.hasDefaultValue() && hadDefaultValue) {
                        myHolder.markError(param, PyLocalize.annNonDefaultParamAfterDefault().get());
                    }
                }

                @Override
                public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
                    inTuple--;
                }

                @Override
                public void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last) {
                    // Synthetic separators don't appear here: this annotator walks PSI-backed parameters only.
                    if (param == null) {
                        return;
                    }
                    if (hadSlash) {
                        myHolder.markError(param, PyLocalize.annMultipleSlash().get());
                    }
                    hadSlash = true;
                    if (hadPositionalContainer) {
                        myHolder.markError(param, PyLocalize.annSlashParamAfterVararg().get());
                    }
                    else if (hadKeywordContainer) {
                        myHolder.markError(param, PyLocalize.annSlashParamAfterKeyword().get());
                    }
                    if (first) {
                        myHolder.markError(param, PyLocalize.annNamedParametersBeforeSlash().get());
                    }
                }

                @Override
                public void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last) {
                    // Synthetic separators don't appear here: this annotator walks PSI-backed parameters only.
                    if (param == null) {
                        return;
                    }
                    if (hadPositionalContainer || hadSingleStar) {
                        myHolder.markError(param, PyLocalize.annMultipleArgs().get());
                    }
                    hadSingleStar = true;
                    if (last) {
                        myHolder.markError(param, PyLocalize.annNamedParametersAfterStar().get());
                    }
                }
            }
        );
    }
}
