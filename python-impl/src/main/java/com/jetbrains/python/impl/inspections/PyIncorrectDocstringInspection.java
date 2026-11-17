// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PlainDocString;
import com.jetbrains.python.impl.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class PyIncorrectDocstringInspection extends PyBaseDocstringInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameIncorrectDocstring();
    }

    @Override
    public Visitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session, Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session)) {

            @Override
            protected void checkDocString(PyDocStringOwner node) {
                PyStringLiteralExpression docstringExpr = node.getDocStringExpression();
                if (docstringExpr != null) {
                    checkParameters(node, docstringExpr);
                }
            }

            private void checkParameters(PyDocStringOwner pyDocStringOwner, PyStringLiteralExpression node) {
                StructuredDocString docString = DocStringUtil.parseDocString(node);
                if (docString instanceof PlainDocString) {
                    return;
                }

                if (pyDocStringOwner instanceof PyFunction) {
                    PyParameter[] realParams = ((PyFunction)pyDocStringOwner).getParameterList().getParameters();

                    List<PyNamedParameter> missingParams = getMissingParams(docString, realParams);
                    if (!missingParams.isEmpty()) {
                        for (PyNamedParameter param : missingParams) {
                            registerProblem(param,
                                            PyLocalize.inspMissingParameterInDocstring(param.getName()).get(),
                                            new DocstringQuickFix(param, null));
                        }
                    }
                    List<Substring> unexpectedParams = getUnexpectedParams(docString, realParams);
                    if (!unexpectedParams.isEmpty()) {
                        for (Substring param : unexpectedParams) {
                            ProblemsHolder holder = getHolder();

                            if (holder != null) {
                                holder.registerProblem(node, param.getTextRange(),
                                                       PyLocalize.inspUnexpectedParameterInDocstring(param).get(),
                                                       new DocstringQuickFix(null, param.getValue()));
                            }
                        }
                    }
                }
            }
        };
    }

    private static List<PyNamedParameter> getMissingParams(StructuredDocString docString,
                                                                    PyParameter [] realParams) {
        List<PyNamedParameter> missing = new ArrayList<>();
        List<String> docStringParameters = docString.getParameters();
        if (docStringParameters.isEmpty()) {
            return Collections.emptyList();
        }

        for (PyParameter p : realParams) {
            PyNamedParameter named = as(p, PyNamedParameter.class);
            if (p.isSelf() || named == null || named.isPositionalContainer() || named.isKeywordContainer()) {
                continue;
            }
            if (!docStringParameters.contains(p.getName())) {
                missing.add((PyNamedParameter)p);
            }
        }
        return missing;
    }

    private static List<Substring> getUnexpectedParams(StructuredDocString docString, PyParameter [] realParams) {
        Map<String, Substring> unexpected = Maps.newHashMap();

        for (Substring s : docString.getParameterSubstrings()) {
            unexpected.put(s.toString(), s);
        }

        for (PyParameter p : realParams) {
            unexpected.remove(p.getName());
        }
        return Lists.newArrayList(unexpected.values());
    }
}
