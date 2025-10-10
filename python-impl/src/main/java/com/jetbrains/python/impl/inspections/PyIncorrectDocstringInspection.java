/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PlainDocString;
import com.jetbrains.python.impl.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyIncorrectDocstringInspection extends PyBaseDocstringInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameIncorrectDocstring();
    }

    @Nonnull
    @Override
    public Visitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session) {

            @Override
            protected void checkDocString(@Nonnull PyDocStringOwner node) {
                final PyStringLiteralExpression docstringExpr = node.getDocStringExpression();
                if (docstringExpr != null) {
                    checkParameters(node, docstringExpr);
                }
            }

            private void checkParameters(@Nonnull PyDocStringOwner pyDocStringOwner, @Nonnull PyStringLiteralExpression node) {
                final StructuredDocString docString = DocStringUtil.parseDocString(node);
                if (docString instanceof PlainDocString) {
                    return;
                }

                if (pyDocStringOwner instanceof PyFunction) {
                    final PyParameter[] realParams = ((PyFunction) pyDocStringOwner).getParameterList().getParameters();

                    final List<PyNamedParameter> missingParams = getMissingParams(docString, realParams);
                    if (!missingParams.isEmpty()) {
                        for (PyNamedParameter param : missingParams) {
                            registerProblem(
                                param,
                                PyLocalize.inspMissingParameterInDocstring(param.getName()).get(),
                                new DocstringQuickFix(param, null)
                            );
                        }
                    }
                    final List<Substring> unexpectedParams = getUnexpectedParams(docString, realParams);
                    if (!unexpectedParams.isEmpty()) {
                        for (Substring param : unexpectedParams) {
                            final ProblemsHolder holder = getHolder();

                            if (holder != null) {
                                holder.newProblem(PyLocalize.inspUnexpectedParameterInDocstring(param))
                                    .range(node, param.getTextRange())
                                    .withFix(new DocstringQuickFix(null, param.getValue()))
                                    .create();
                            }
                        }
                    }
                }
            }
        };
    }

    @Nonnull
    private static List<PyNamedParameter> getMissingParams(@Nonnull StructuredDocString docString, @Nonnull PyParameter[] realParams) {
        final List<PyNamedParameter> missing = new ArrayList<>();
        final List<String> docStringParameters = docString.getParameters();
        if (docStringParameters.isEmpty()) {
            return Collections.emptyList();
        }

        for (PyParameter p : realParams) {
            final PyNamedParameter named = as(p, PyNamedParameter.class);
            if (p.isSelf() || named == null || named.isPositionalContainer() || named.isKeywordContainer()) {
                continue;
            }
            if (!docStringParameters.contains(p.getName())) {
                missing.add((PyNamedParameter) p);
            }
        }
        return missing;
    }

    @Nonnull
    private static List<Substring> getUnexpectedParams(@Nonnull StructuredDocString docString, @Nonnull PyParameter[] realParams) {
        final Map<String, Substring> unexpected = Maps.newHashMap();

        for (Substring s : docString.getParameterSubstrings()) {
            unexpected.put(s.toString(), s);
        }

        for (PyParameter p : realParams) {
            if (unexpected.containsKey(p.getName())) {
                unexpected.remove(p.getName());
            }
        }
        return Lists.newArrayList(unexpected.values());
    }
}
