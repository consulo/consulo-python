// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.statistics;

import com.jetbrains.python.impl.inspections.PyCompatibilityInspection;
import com.jetbrains.python.impl.inspections.PyCompatibilityInspectionState;
import consulo.annotation.component.ExtensionImpl;
import consulo.externalService.statistic.AbstractApplicationUsagesCollector;
import consulo.externalService.statistic.CollectUsagesException;
import consulo.externalService.statistic.UsageDescriptor;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.project.Project;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Consulo: JB's FUS ProjectUsagesCollector (EventLogGroup "python.inspection.compatibility", event "python.versions")
 * is mapped to Consulo's {@link AbstractApplicationUsagesCollector}, one usage per configured version.
 */
@ExtensionImpl
public class PyCompatibilityInspectionCollector extends AbstractApplicationUsagesCollector {
    private static final String INSPECTION_SHORT_NAME = "PyCompatibilityInspection";

    @Override
    public Set<UsageDescriptor> getProjectUsages(Project project) throws CollectUsagesException {
        InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        if (!inspectionProfile.isToolEnabled(HighlightDisplayKey.find(INSPECTION_SHORT_NAME))) {
            return Set.of();
        }
        InspectionToolWrapper<?> wrapper = inspectionProfile.getInspectionTool(INSPECTION_SHORT_NAME, project);
        if (wrapper == null || !(wrapper.getTool() instanceof PyCompatibilityInspection)) {
            return Set.of();
        }
        // Consulo: JB reads PyCompatibilityInspection.ourVersions, Consulo keeps them in PyCompatibilityInspectionState
        if (!(wrapper.getToolState().getState() instanceof PyCompatibilityInspectionState state)) {
            return Set.of();
        }
        Set<UsageDescriptor> result = new LinkedHashSet<>();
        for (String version : state.versions) {
            result.add(new UsageDescriptor(version, 1));
        }
        return result;
    }

    @Override
    public String getGroupId() {
        return "python.inspection.compatibility";
    }
}
