package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class PythonLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    @Nonnull
    public String groupId() {
        return "python";
    }

    @Override
    @Nonnull
    public LocalizeValue groupName() {
        return LocalizeValue.localizeTODO("Python");
    }

    @Override
    public void contribute(@Nonnull Factory factory) {
        try (Builder builder = factory.newBuilder("pythonSuper", "super", "super($class$, self).$method$($end$)", LocalizeValue.localizeTODO("Generates a 'super' call"))) {
            builder.withVariable("class", "pyClassName()", "", false);
            builder.withVariable("method", "pyFunctionName()", "", false);
            builder.withVariable("end", "", "", true);

            builder.withContext(PythonTemplateContextType.class, true);
        }
    }
}
