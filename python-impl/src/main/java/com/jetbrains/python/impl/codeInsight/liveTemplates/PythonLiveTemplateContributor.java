// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;

/**
 * Ported from JB {@code liveTemplates/Python.xml}.
 */
@ExtensionImpl
public class PythonLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    public String groupId() {
        return "python";
    }

    @Override
    public LocalizeValue groupName() {
        return LocalizeValue.of("Python");
    }

    @Override
    public void contribute(Factory factory) {
        try (Builder builder = factory.newBuilder("pythonSuper", "super", "super($args$).$method$($end$)", PyCodeInsightLocalize.liveTemplateSuperDescription())) {
            builder.withVariable("args", "pyVerSpecificSuperSignature()", "", false);
            builder.withVariable("method", "pyFunctionName()", "", false);
            builder.withVariable("end", "", "", true);

            builder.withContext(PythonTemplateContextType.Class.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonMain", "main", "if __name__ == '__main__':\n    $END$", PyCodeInsightLocalize.liveTemplateMainDescription())) {
            builder.withContext(PythonTemplateContextType.TopLevel.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonIter", "iter", "for $VAR$ in $ITERABLE$:\n    $END$", PyCodeInsightLocalize.liveTemplateIterDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonItere", "itere", "for $INDEX$, $VAR$ in enumerate($ITERABLE$):\n    $END$", PyCodeInsightLocalize.liveTemplateItereDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("INDEX", "", "\"i\"", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompl", "compl", "[$VAR_EXPR$ for $VAR$ in $ITERABLE$]", PyCodeInsightLocalize.liveTemplateComplDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("VAR_EXPR", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompli", "compli", "[$VAR_EXPR$ for $VAR$ in $ITERABLE$ if $VAR_EXPR_IF$]", PyCodeInsightLocalize.liveTemplateCompliDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("VAR_EXPR", "", "VAR", true);
            builder.withVariable("VAR_EXPR_IF", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompg", "compg", "($VAR_EXPR$ for $VAR$ in $ITERABLE$)", PyCodeInsightLocalize.liveTemplateCompgDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("VAR_EXPR", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompgi", "compgi", "($VAR_EXPR$ for $VAR$ in $ITERABLE$ if $VAR_EXPR_IF$)", PyCodeInsightLocalize.liveTemplateCompgiDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("VAR_EXPR", "", "VAR", true);
            builder.withVariable("VAR_EXPR_IF", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonComps", "comps", "{$VAR_EXPR$ for $VAR$ in $ITERABLE$}", PyCodeInsightLocalize.liveTemplateCompsDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("VAR_EXPR", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompsi", "compsi", "{$VAR_EXPR$ for $VAR$ in $ITERABLE$ if $VAR_EXPR_IF$}", PyCodeInsightLocalize.liveTemplateCompsiDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("VAR_EXPR", "", "VAR", true);
            builder.withVariable("VAR_EXPR_IF", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompd", "compd", "{$KEY_EXPR$: $VAL_EXPR$ for $VAR$ in $ITERABLE$}", PyCodeInsightLocalize.liveTemplateCompdDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("KEY_EXPR", "", "VAR", true);
            builder.withVariable("VAL_EXPR", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonCompdi", "compdi", "{$KEY_EXPR$: $VAL_EXPR$ for $VAR$ in $ITERABLE$ if $VAR_EXPR_IF$}", PyCodeInsightLocalize.liveTemplateCompdiDescription())) {
            builder.withVariable("ITERABLE", "pyIterableVariable()", "", true);
            builder.withVariable("VAR", "collectionElementName(ITERABLE)", "", true);
            builder.withVariable("KEY_EXPR", "", "VAR", true);
            builder.withVariable("VAL_EXPR", "", "VAR", true);
            builder.withVariable("VAR_EXPR_IF", "", "VAR", true);

            builder.withContext(PythonTemplateContextType.General.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonProp", "prop", "@property\ndef $NAME$(self):\n    return $END$", PyCodeInsightLocalize.liveTemplatePropDescription())) {
            builder.withVariable("NAME", "", "", true);

            builder.withContext(PythonTemplateContextType.Class.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonProps", "props", "@property\ndef $NAME$(self):\n    return $END$\n\n@$NAME$.setter\ndef $NAME$(self, value):\n    pass", PyCodeInsightLocalize.liveTemplatePropsDescription())) {
            builder.withVariable("NAME", "", "", true);

            builder.withContext(PythonTemplateContextType.Class.class, true);
        }

        try (Builder builder = factory.newBuilder("pythonPropsd", "propsd", "@property\ndef $NAME$(self):\n    return $END$\n\n@$NAME$.setter\ndef $NAME$(self, value):\n    pass\n\n@$NAME$.deleter\ndef $NAME$(self):\n    pass", PyCodeInsightLocalize.liveTemplatePropsdDescription())) {
            builder.withVariable("NAME", "", "", true);

            builder.withContext(PythonTemplateContextType.Class.class, true);
        }
    }
}
