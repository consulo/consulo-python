// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.impl.inspection.InspectionEngine;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the suppression codes of the problems currently on a given line, so the "Specify inspection codes"
 * quick fix ({@code com.jetbrains.python.inspections.typeignore.PyTypeIgnoreWithoutCodeInspection}) can turn a
 * code-less `# type: ignore` into one that lists exactly what it covers.
 * <p>
 * It runs the enabled local inspections restricted to that one line with `ignoreSuppressedElements = false`,
 * so problems that a bare `# type: ignore` on the line would otherwise hide are still reported. Must be called
 * under a read action off the EDT (e.g. from a ModCommand quick fix).
 */
public final class PyIgnoreCodeComputer {
    private PyIgnoreCodeComputer() {
    }

    /**
     * The ordered, de-duplicated codes for the problems on {@code lineNumber} of {@code file} (a `PyTypeChecker` problem
     * contributes its granular code such as `unsupported-operator`; any other inspection contributes its
     * kebab-case alias or raw suppress id). {@code excludeToolIds} drops inspections whose own diagnostics should not
     * be listed (notably the inspection that offers this fix).
     */
    @RequiredReadAction
    public static List<String> codesOnLine(PsiFile file, int lineNumber, Set<String> excludeToolIds) {
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return Collections.emptyList();
        }
        if (lineNumber < 0 || lineNumber >= document.getLineCount()) {
            return Collections.emptyList();
        }
        TextRange lineRange = new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));

        InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        List<LocalInspectionToolWrapper> wrappers = new ArrayList<>();
        for (InspectionToolWrapper<?> tool : profile.getInspectionTools(file)) {
            if (tool instanceof LocalInspectionToolWrapper wrapper &&
                !excludeToolIds.contains(wrapper.getID()) &&
                profile.isToolEnabled(HighlightDisplayKey.find(wrapper.getShortName()), file)) {
                wrappers.add(wrapper);
            }
        }
        if (wrappers.isEmpty()) {
            return Collections.emptyList();
        }

        ProgressIndicator globalIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        ProgressIndicator indicator = globalIndicator != null ? globalIndicator : new EmptyProgressIndicator();
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> perTool = InspectionEngine.inspectEx(
            wrappers, file, lineRange, lineRange,
            /* isOnTheFly = */ false, /* inspectInjectedPsi = */ false, /* ignoreSuppressedElements = */ false,
            indicator, (toolWrapper, descriptor) -> true
        );

        Set<String> codes = new LinkedHashSet<>();
        for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : perTool.entrySet()) {
            LocalInspectionToolWrapper wrapper = entry.getKey();
            for (ProblemDescriptor descriptor : entry.getValue()) {
                if (descriptor.getLineNumber() != lineNumber) {
                    continue;
                }
                if (descriptor.getProblemGroup() instanceof PyTypeCheckerSuppressableProblemGroup group) {
                    codes.add(group.getCodeId());
                }
                else {
                    String id = wrapper.getID();
                    String code = PySuppressionUtil.toSuppressionCode(id);
                    codes.add(code != null ? code : id);
                }
            }
        }
        return new ArrayList<>(codes);
    }
}
