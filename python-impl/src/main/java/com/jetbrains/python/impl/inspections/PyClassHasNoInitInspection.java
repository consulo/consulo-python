// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.impl.inspections.quickfix.AddMethodQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * User: ktisha
 * See pylint W0232
 */
@ExtensionImpl
public final class PyClassHasNoInitInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameClassHasNoInit();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonPython2();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            PyClass outerClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
            assert node != null;
            if (outerClass != null && StringUtil.equalsIgnoreCase("meta", node.getName())) {
                return;
            }
            List<PyClassLikeType> types = node.getAncestorTypes(myTypeEvalContext);
            if (PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor(node, myTypeEvalContext)) {
                return;
            }
            for (PyClassLikeType type : types) {
                if (type == null) {
                    return;
                }
                String qName = type.getClassQName();
                if (qName != null && qName.contains(PyNames.TEST_CASE)) {
                    return;
                }
                if (!(type instanceof PyClassType)) {
                    return;
                }
            }

            PyFunction init = node.findInitOrNew(true, myTypeEvalContext);
            if (init == null) {
                registerProblem(node.getNameIdentifier(), PyLocalize.inspClassHasNoInit().get(),
                                new AddMethodQuickFix(PyNames.INIT, node.getName(), false));
            }
        }
    }
}
